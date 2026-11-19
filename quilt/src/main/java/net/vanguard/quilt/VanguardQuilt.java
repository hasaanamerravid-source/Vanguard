package net.vanguard.quilt;

import net.fabricmc.api.ClientModInitializer;
import net.vanguard.Vanguard;

/**
 * Quilt Loader ships the Fabric loader API and loads Fabric-format mods. This
 * build keeps the Fabric metadata format on purpose so it runs on Quilt
 * without pulling in QSL for a single entry point.
 */
public final class VanguardQuilt implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Vanguard.init();
    }
}
