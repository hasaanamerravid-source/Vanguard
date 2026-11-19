package net.vanguard.culling;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.vanguard.config.VanguardConfig;
import org.joml.Vector3fc;

import java.util.Locale;

/**
 * Per-frame culling state and statistics.
 *
 * All methods are called from the client render thread. The only persistent
 * allocations are the predictive frustum, the animation cache, and the
 * bounded occlusion caches; the steady-state hot path allocates nothing.
 */
public final class EntityCuller {
    private static final EntityCuller INSTANCE = new EntityCuller();
    private static final int STATE_CACHE_LIMIT = 2048;

    private final VanguardConfig config = VanguardConfig.get();
    private final Int2ObjectOpenHashMap<CachedState> stateCache = new Int2ObjectOpenHashMap<>();

    private Frustum predictiveFrustum;
    private Frustum predictiveBase;
    private boolean predictiveActive;
    private boolean cameraValid;
    private boolean hasCameraSample;

    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float forwardX;
    private float forwardY;
    private float forwardZ;
    private float fov;

    private double lastCameraX;
    private double lastCameraY;
    private double lastCameraZ;
    private float lastYaw;
    private float lastPitch;

    private long frame;
    private int total;
    private int rendered;
    private int culled;
    private int occluded;
    private int throttled;
    private int particlesCulled;
    private int blockEntitiesCulled;

    private EntityCuller() {
    }

    public static EntityCuller get() {
        return INSTANCE;
    }

    public void beginFrame(Camera camera) {
        this.frame++;
        this.total = 0;
        this.rendered = 0;
        this.culled = 0;
        this.occluded = 0;
        this.throttled = 0;
        this.particlesCulled = 0;
        this.blockEntitiesCulled = 0;
        this.cameraValid = camera != null;
        this.predictiveActive = false;

        if (!this.cameraValid) {
            this.hasCameraSample = false;
            return;
        }

        Vec3 position = camera.position();
        this.cameraX = position.x();
        this.cameraY = position.y();
        this.cameraZ = position.z();

        Vector3fc forward = camera.forwardVector();
        this.forwardX = forward.x();
        this.forwardY = forward.y();
        this.forwardZ = forward.z();
        this.fov = camera.getFov();

        double cameraSpeed = 0.0;
        float yawDelta = 0.0F;
        float pitchDelta = 0.0F;
        if (this.hasCameraSample) {
            double dx = this.cameraX - this.lastCameraX;
            double dy = this.cameraY - this.lastCameraY;
            double dz = this.cameraZ - this.lastCameraZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < 1024.0) {
                cameraSpeed = Math.sqrt(distanceSq);
                yawDelta = (float) Math.abs(Mth.wrapDegrees((double) (camera.yRot() - this.lastYaw)));
                pitchDelta = (float) Math.abs(Mth.wrapDegrees((double) (camera.xRot() - this.lastPitch)));
            }
        }

        this.lastCameraX = this.cameraX;
        this.lastCameraY = this.cameraY;
        this.lastCameraZ = this.cameraZ;
        this.lastYaw = camera.yRot();
        this.lastPitch = camera.xRot();
        this.hasCameraSample = true;

        preparePredictiveFrustum(camera, cameraSpeed, yawDelta, pitchDelta);
        OcclusionCuller.get().beginFrame(this.cameraX, this.cameraY, this.cameraZ, this.config);
    }

    private void preparePredictiveFrustum(Camera camera, double cameraSpeed, float yawDelta, float pitchDelta) {
        if (!this.config.enabled || !this.config.predictiveCulling || this.config.predictionStrength <= 0.0) {
            return;
        }

        Frustum base = camera.getCullFrustum();
        if (base == null) {
            return;
        }

        double offset = 1.5
                + Math.min(4.0, (yawDelta + pitchDelta) * 0.2)
                + Math.min(2.0, cameraSpeed * 2.0);
        offset = Math.min(offset * this.config.predictionStrength, this.config.maxPredictionOffset);
        if (offset <= 0.01) {
            return;
        }

        if (this.predictiveFrustum == null) {
            this.predictiveFrustum = new Frustum(base);
        } else {
            this.predictiveFrustum.set(base);
        }
        // Negative offset moves the frustum origin backwards along the view
        // direction, keeping entities that are about to enter the screen.
        this.predictiveFrustum.offset((float) -offset);
        this.predictiveBase = base;
        this.predictiveActive = true;
    }

    /**
     * Substituted for the frustum argument in
     * {@code LevelExtractor.isEntityVisible}.
     */
    public Frustum frustumFor(Frustum frustum) {
        return this.predictiveActive && frustum == this.predictiveBase ? this.predictiveFrustum : frustum;
    }

    public void onCandidate() {
        this.total++;
    }

    public void onRendered() {
        this.rendered++;
    }

    public void onRejected() {
        this.culled++;
    }

    public void onOccluded() {
        this.culled++;
        this.occluded++;
    }

    public void onBlockEntityCulled() {
        this.blockEntitiesCulled++;
    }

    public void onParticleCulled() {
        this.particlesCulled++;
    }

    /**
     * Returns the cached state when this entity is currently in the lowest
     * level of detail and its refresh interval has not elapsed. Returning
     * null asks vanilla to run a full extraction, which also refreshes the
     * cache.
     */
    public EntityRenderState reuseState(Entity entity, float partialTicks) {
        if (!canDecimate(entity)) {
            return null;
        }

        CachedState cached = this.stateCache.get(entity.getId());
        if (cached == null
                || cached.state.entityType != entity.getType()
                || this.frame - cached.frame >= this.config.animationRefreshFrames) {
            return null;
        }

        EntityRenderState state = cached.state;
        updatePosition(state, entity, partialTicks);
        // The cached state was finalized on a previous frame. Its shadow
        // pieces would otherwise be submitted again at the old position.
        state.shadowRadius = 0.0F;
        state.shadowPieces.clear();
        this.throttled++;
        return state;
    }

    public void storeState(Entity entity, EntityRenderState state) {
        if (state == null || !canDecimate(entity)) {
            return;
        }
        if (this.stateCache.size() >= STATE_CACHE_LIMIT) {
            this.stateCache.clear();
        }
        this.stateCache.put(entity.getId(), new CachedState(state, this.frame));
    }

    public boolean shouldSkipShadows(Entity entity) {
        if (!this.config.enabled || !this.config.foveatedLod || !this.cameraValid) {
            return false;
        }
        return lodLevel(entity) >= 1;
    }

    public boolean shouldCullParticle(double x, double y, double z, boolean overrideLimiter, boolean alwaysShow) {
        if (!this.config.enabled || !this.config.particleCulling || overrideLimiter || alwaysShow || !this.cameraValid) {
            return false;
        }

        double dx = x - this.cameraX;
        double dy = y - this.cameraY;
        double dz = z - this.cameraZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq <= this.config.particleMinDistance * this.config.particleMinDistance) {
            return false;
        }

        double inverseDistance = 1.0 / Math.sqrt(distanceSq);
        double dot = (dx * this.forwardX + dy * this.forwardY + dz * this.forwardZ) * inverseDistance;
        if (dot <= 0.0) {
            return true;
        }

        double halfFov = Math.max(10.0, this.fov * 0.5);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angle / halfFov > this.config.particleFovRatio;
    }

    public boolean isDebugLineEnabled() {
        return this.config.debugLine;
    }

    public String debugLine() {
        if (!this.config.enabled) {
            return "Vanguard: disabled";
        }
        return String.format(
                Locale.ROOT,
                "Vanguard  total: %d  rendered: %d  throttled: %d  culled: %d (occluded: %d)  particles: %d  block entities: %d",
                this.total,
                this.rendered,
                this.throttled,
                this.culled,
                this.occluded,
                this.particlesCulled,
                this.blockEntitiesCulled
        );
    }

    private boolean canDecimate(Entity entity) {
        if (!this.config.enabled || !this.config.foveatedLod || !this.cameraValid) {
            return false;
        }
        if (entity instanceof Player
                || entity.isSpectator()
                || entity.isInvisible()
                || entity.isCurrentlyGlowing()
                || entity.hasCustomName()) {
            return false;
        }
        if (entity.tickCount < 20 || entity.isPassenger() || entity.isVehicle()) {
            return false;
        }
        if (entity instanceof Leashable leashable && leashable.isLeashed()) {
            return false;
        }
        return lodLevel(entity) >= 2;
    }

    private int lodLevel(Entity entity) {
        if (!this.config.enabled || !this.config.foveatedLod || !this.cameraValid) {
            return 0;
        }

        double dx = entity.getX() - this.cameraX;
        double dy = entity.getY() + entity.getBbHeight() * 0.5 - this.cameraY;
        double dz = entity.getZ() - this.cameraZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < 0.0001) {
            return 0;
        }

        double inverseDistance = 1.0 / Math.sqrt(distanceSq);
        double dot = (dx * this.forwardX + dy * this.forwardY + dz * this.forwardZ) * inverseDistance;
        double halfFov = Math.max(10.0, this.fov * 0.5);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        double eccentricity = angle / halfFov;

        double nearSq = this.config.lodNearDistance * this.config.lodNearDistance;
        double farSq = this.config.lodFarDistance * this.config.lodFarDistance;

        if (distanceSq > farSq || eccentricity > 1.05) {
            return 2;
        }
        if (distanceSq > nearSq && eccentricity > this.config.lodFoveaRatio) {
            return 2;
        }
        if (distanceSq > nearSq || eccentricity > this.config.lodFoveaRatio * 0.8) {
            return 1;
        }
        return 0;
    }

    private void updatePosition(EntityRenderState state, Entity entity, float partialTicks) {
        state.x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        state.y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        state.z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());

        double dx = state.x - this.cameraX;
        double dy = state.y - this.cameraY;
        double dz = state.z - this.cameraZ;
        state.distanceToCameraSq = dx * dx + dy * dy + dz * dz;
    }

    private static final class CachedState {
        private final EntityRenderState state;
        private final long frame;

        private CachedState(EntityRenderState state, long frame) {
            this.state = state;
            this.frame = frame;
        }
    }
}
