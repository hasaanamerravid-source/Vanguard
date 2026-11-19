package net.vanguard.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.vanguard.Vanguard;

public final class VanguardFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Vanguard.init();
    }
}
