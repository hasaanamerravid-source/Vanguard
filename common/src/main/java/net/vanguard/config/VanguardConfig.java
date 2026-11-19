package net.vanguard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.vanguard.Vanguard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON configuration. The file is read once, on the render thread, the
 * first time an optimization needs a setting. There is deliberately no config
 * GUI: editing config/vanguard.json and restarting is the supported workflow.
 */
public final class VanguardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VanguardConfig instance;

    public boolean enabled = true;

    public boolean predictiveCulling = true;
    public double predictionStrength = 1.0;
    public double maxPredictionOffset = 6.0;

    public boolean occlusionCulling = true;
    public int occlusionBudget = 24;
    public int occlusionCacheMillis = 120;
    public double occlusionMinDistance = 4.0;
    public double occlusionMaxDistance = 96.0;

    public boolean blockEntityOcclusion = true;
    public int blockEntityOcclusionBudget = 32;

    public boolean foveatedLod = true;
    public double lodNearDistance = 24.0;
    public double lodFarDistance = 64.0;
    public double lodFoveaRatio = 0.7;
    public int animationRefreshFrames = 4;

    public boolean particleCulling = true;
    public double particleMinDistance = 12.0;
    public double particleFovRatio = 1.0;

    public boolean debugLine = true;

    /**
     * Client-thread lazy initialization. Every caller is on the render thread,
     * so no synchronization is required.
     */
    public static VanguardConfig get() {
        VanguardConfig config = instance;
        if (config == null) {
            config = load();
            instance = config;
        }
        return config;
    }

    private static VanguardConfig load() {
        Path path = configPath();
        boolean existed = Files.exists(path);
        VanguardConfig config = new VanguardConfig();

        if (existed) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                VanguardConfig parsed = GSON.fromJson(reader, VanguardConfig.class);
                if (parsed != null) {
                    config = parsed;
                }
            } catch (IOException | JsonParseException exception) {
                Vanguard.LOGGER.warn("Could not read {}; using defaults", path, exception);
            }
        }

        config.validate();

        if (!existed) {
            config.save(path);
        }
        return config;
    }

    private static Path configPath() {
        Minecraft minecraft = Minecraft.getInstance();
        Path gameDirectory = minecraft == null ? Path.of(".") : minecraft.gameDirectory.toPath();
        return gameDirectory.resolve("config").resolve("vanguard.json");
    }

    private void validate() {
        predictionStrength = clamp(predictionStrength, 0.0, 4.0, 1.0);
        maxPredictionOffset = clamp(maxPredictionOffset, 0.0, 16.0, 6.0);

        occlusionBudget = clamp(occlusionBudget, 0, 128);
        occlusionCacheMillis = clamp(occlusionCacheMillis, 0, 2000);
        occlusionMinDistance = clamp(occlusionMinDistance, 0.0, 32.0, 4.0);
        occlusionMaxDistance = clamp(occlusionMaxDistance, 16.0, 256.0, 96.0);
        if (occlusionMaxDistance <= occlusionMinDistance) {
            occlusionMaxDistance = occlusionMinDistance + 1.0;
        }

        blockEntityOcclusionBudget = clamp(blockEntityOcclusionBudget, 0, 128);

        lodNearDistance = clamp(lodNearDistance, 4.0, 128.0, 24.0);
        lodFarDistance = clamp(lodFarDistance, lodNearDistance, 256.0, 64.0);
        lodFoveaRatio = clamp(lodFoveaRatio, 0.1, 2.0, 0.7);
        animationRefreshFrames = clamp(animationRefreshFrames, 2, 20);

        particleMinDistance = clamp(particleMinDistance, 0.0, 64.0, 12.0);
        particleFovRatio = clamp(particleFovRatio, 0.1, 3.0, 1.0);
    }

    private void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            Vanguard.LOGGER.warn("Could not write {}", path, exception);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
