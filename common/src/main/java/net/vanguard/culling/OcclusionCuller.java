package net.vanguard.culling;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.vanguard.config.VanguardConfig;

/**
 * Conservative occlusion tests built on an allocation-free voxel walk.
 *
 * An entity is culled only when every sample ray from the camera to its
 * bounding box is stopped by a full, opaque block. The walk treats glass,
 * leaves, fences, fluids, and the entity's own block as clear, so the result
 * never hides something vanilla would draw through a non-opaque surface.
 *
 * The walk advances cell by cell along the dominant axis, exactly like a
 * DDA grid traversal. Only blocks whose {@link net.minecraft.world.level.block.state.BlockState#isSolidRender()}
 * is true count as occluders; that predicate is already used by vanilla for
 * full opaque cubes.
 */
public final class OcclusionCuller {
    private static final OcclusionCuller INSTANCE = new OcclusionCuller();
    private static final int CACHE_LIMIT = 4096;
    private static final int MAX_RAY_STEPS = 1024;
    private static final double CAMERA_CACHE_RESET_DISTANCE_SQ = 2.25;

    private final Int2ObjectOpenHashMap<EntitySample> entitySamples = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockEntitySample> blockEntitySamples = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private VanguardConfig config;
    private Level level;
    private double lastCameraX;
    private double lastCameraY;
    private double lastCameraZ;
    private boolean hasCameraSample;
    private boolean cameraInSolid;
    private int entityTests;
    private int blockEntityTests;

    private OcclusionCuller() {
    }

    public static OcclusionCuller get() {
        return INSTANCE;
    }

    void beginFrame(double cameraX, double cameraY, double cameraZ, VanguardConfig config) {
        this.config = config;
        this.entityTests = 0;
        this.blockEntityTests = 0;

        Minecraft minecraft = Minecraft.getInstance();
        Level currentLevel = minecraft == null ? null : minecraft.level;
        if (currentLevel != this.level) {
            this.entitySamples.clear();
            this.blockEntitySamples.clear();
            this.level = currentLevel;
            this.hasCameraSample = false;
        }

        double movedX = cameraX - this.lastCameraX;
        double movedY = cameraY - this.lastCameraY;
        double movedZ = cameraZ - this.lastCameraZ;
        if (!this.hasCameraSample
                || movedX * movedX + movedY * movedY + movedZ * movedZ > CAMERA_CACHE_RESET_DISTANCE_SQ) {
            this.entitySamples.clear();
            this.blockEntitySamples.clear();
            this.lastCameraX = cameraX;
            this.lastCameraY = cameraY;
            this.lastCameraZ = cameraZ;
            this.hasCameraSample = true;
        }

        this.cursor.set(Mth.floor(cameraX), Mth.floor(cameraY), Mth.floor(cameraZ));
        this.cameraInSolid = currentLevel != null && currentLevel.getBlockState(this.cursor).isSolidRender();
    }

    /**
     * Entity counterpart used by {@code LevelExtractor.isEntityVisible}.
     */
    public boolean isEntityOccluded(Entity entity, double cameraX, double cameraY, double cameraZ) {
        VanguardConfig config = this.config;
        if (config == null || !config.enabled || !config.occlusionCulling || this.cameraInSolid) {
            return false;
        }
        Level entityLevel = entity.level();
        if (entityLevel == null || entityLevel != this.level) {
            return false;
        }
        if (!isEntityEligible(entity, cameraX, cameraY, cameraZ, config)) {
            return false;
        }

        int entityId = entity.getId();
        long now = Util.getMillis();
        EntitySample sample = this.entitySamples.get(entityId);
        if (sample != null && sample.expiresAt >= now
                && Math.abs(sample.x - entity.getX()) < 1.0
                && Math.abs(sample.y - entity.getY()) < 1.0
                && Math.abs(sample.z - entity.getZ()) < 1.0) {
            return sample.occluded;
        }

        if (this.entityTests >= config.occlusionBudget) {
            return false;
        }
        this.entityTests++;

        boolean occluded = testEntity(entityLevel, entity, cameraX, cameraY, cameraZ);
        if (this.entitySamples.size() >= CACHE_LIMIT) {
            this.entitySamples.clear();
        }
        this.entitySamples.put(entityId, new EntitySample(
                occluded,
                now + config.occlusionCacheMillis,
                entity.getX(),
                entity.getY(),
                entity.getZ()
        ));
        return occluded;
    }

    /**
     * Block-entity counterpart used by the block entity render dispatcher.
     */
    public boolean isBlockEntityOccluded(BlockEntity blockEntity, boolean globallyRendered, Vec3 cameraPosition) {
        VanguardConfig config = this.config;
        if (cameraPosition == null || globallyRendered || config == null
                || !config.enabled || !config.blockEntityOcclusion || this.cameraInSolid) {
            return false;
        }
        if (blockEntity.isRemoved() || !blockEntity.hasLevel()) {
            return false;
        }
        Level blockEntityLevel = blockEntity.getLevel();
        if (blockEntityLevel == null || blockEntityLevel != this.level) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        double targetX = pos.getX() + 0.5;
        double targetY = pos.getY() + 0.5;
        double targetZ = pos.getZ() + 0.5;
        double dx = targetX - cameraPosition.x;
        double dy = targetY - cameraPosition.y;
        double dz = targetZ - cameraPosition.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < config.occlusionMinDistance * config.occlusionMinDistance
                || distanceSq > config.occlusionMaxDistance * config.occlusionMaxDistance) {
            return false;
        }

        long now = Util.getMillis();
        long key = pos.asLong();
        BlockEntitySample sample = this.blockEntitySamples.get(key);
        if (sample != null && sample.expiresAt >= now) {
            return sample.occluded;
        }

        if (this.blockEntityTests >= config.blockEntityOcclusionBudget) {
            return false;
        }
        this.blockEntityTests++;

        // The block entity's own block is not an occluder; a ray that reaches
        // the block entity must still be considered visible.
        boolean occluded = isBlocked(blockEntityLevel, cameraPosition.x, cameraPosition.y, cameraPosition.z,
                targetX, targetY, targetZ, pos)
                && isBlocked(blockEntityLevel, cameraPosition.x, cameraPosition.y, cameraPosition.z,
                targetX, pos.getY() + 0.9, targetZ, pos);

        if (this.blockEntitySamples.size() >= CACHE_LIMIT) {
            this.blockEntitySamples.clear();
        }
        this.blockEntitySamples.put(key, new BlockEntitySample(occluded, now + config.occlusionCacheMillis));
        return occluded;
    }

    private static boolean isEntityEligible(Entity entity, double cameraX, double cameraY, double cameraZ,
                                            VanguardConfig config) {
        // These entities carry information that can remain visible through a
        // wall, or are handled specially by vanilla.
        if (entity instanceof Player || entity instanceof EnderDragonPart) {
            return false;
        }
        if (entity.isSpectator() || entity.isInvisible() || entity.isCurrentlyGlowing() || entity.hasCustomName()) {
            return false;
        }
        if (entity.tickCount < 20 || entity.isPassenger() || entity.isVehicle()) {
            return false;
        }
        if (entity instanceof Leashable leashable && leashable.isLeashed()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player != null && entity.hasIndirectPassenger(player)) {
            return false;
        }

        double dx = entity.getX() - cameraX;
        double dy = entity.getY() - cameraY;
        double dz = entity.getZ() - cameraZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        return distanceSq >= config.occlusionMinDistance * config.occlusionMinDistance
                && distanceSq <= config.occlusionMaxDistance * config.occlusionMaxDistance;
    }

    private boolean testEntity(Level level, Entity entity, double cameraX, double cameraY, double cameraZ) {
        AABB box = entity.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double offsetX = (box.maxX - box.minX) * 0.3;
        double offsetY = (box.maxY - box.minY) * 0.25;
        double offsetZ = (box.maxZ - box.minZ) * 0.3;

        // Check the nearest, most likely-to-be-visible point first. Visible
        // entities normally cost a single ray.
        if (!isBlocked(level, cameraX, cameraY, cameraZ, centerX, centerY, centerZ, null)) {
            return false;
        }
        if (!isBlocked(level, cameraX, cameraY, cameraZ, centerX, centerY + offsetY, centerZ, null)) {
            return false;
        }
        if (!isBlocked(level, cameraX, cameraY, cameraZ, centerX + offsetX, centerY, centerZ, null)) {
            return false;
        }
        if (!isBlocked(level, cameraX, cameraY, cameraZ, centerX - offsetX, centerY, centerZ, null)) {
            return false;
        }
        if (!isBlocked(level, cameraX, cameraY, cameraZ, centerX, centerY, centerZ + offsetZ, null)) {
            return false;
        }
        return isBlocked(level, cameraX, cameraY, cameraZ, centerX, centerY, centerZ - offsetZ, null);
    }

    /**
     * Allocation-free DDA traversal from the camera to a target point. Returns
     * true only when a full opaque block is crossed before the target cell.
     */
    private boolean isBlocked(Level level, double startX, double startY, double startZ,
                              double endX, double endY, double endZ, BlockPos ignoredPos) {
        int cellX = Mth.floor(startX);
        int cellY = Mth.floor(startY);
        int cellZ = Mth.floor(startZ);
        int endCellX = Mth.floor(endX);
        int endCellY = Mth.floor(endY);
        int endCellZ = Mth.floor(endZ);

        if (cellX == endCellX && cellY == endCellY && cellZ == endCellZ) {
            return false;
        }

        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;

        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
                : (stepX > 0 ? cellX + 1 - startX : startX - cellX) * tDeltaX;
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
                : (stepY > 0 ? cellY + 1 - startY : startY - cellY) * tDeltaY;
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : (stepZ > 0 ? cellZ + 1 - startZ : startZ - cellZ) * tDeltaZ;

        for (int step = 0; step < MAX_RAY_STEPS; step++) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                cellX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                cellY += stepY;
                tMaxY += tDeltaY;
            } else {
                cellZ += stepZ;
                tMaxZ += tDeltaZ;
            }

            // The target cell itself is never an occluder. For block entities
            // it is the block that owns the renderer; for entities it is the
            // cell that contains the sample point.
            if (cellX == endCellX && cellY == endCellY && cellZ == endCellZ) {
                return false;
            }

            this.cursor.set(cellX, cellY, cellZ);
            if (ignoredPos != null && ignoredPos.equals(this.cursor)) {
                continue;
            }
            if (level.getBlockState(this.cursor).isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    private static final class EntitySample {
        private final boolean occluded;
        private final long expiresAt;
        private final double x;
        private final double y;
        private final double z;

        private EntitySample(boolean occluded, long expiresAt, double x, double y, double z) {
            this.occluded = occluded;
            this.expiresAt = expiresAt;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class BlockEntitySample {
        private final boolean occluded;
        private final long expiresAt;

        private BlockEntitySample(boolean occluded, long expiresAt) {
            this.occluded = occluded;
            this.expiresAt = expiresAt;
        }
    }
}
