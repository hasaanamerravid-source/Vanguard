package net.vanguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. The small loader entry points call this once
 * during client initialization; all optimization state is created lazily on
 * the render thread.
 */
public final class Vanguard {
    public static final String MOD_ID = "vanguard";
    public static final String VERSION = "1.0.1";
    public static final Logger LOGGER = LoggerFactory.getLogger("Vanguard");

    private Vanguard() {
    }

    public static void init() {
        LOGGER.info("Vanguard {} initialized", VERSION);
    }
}
