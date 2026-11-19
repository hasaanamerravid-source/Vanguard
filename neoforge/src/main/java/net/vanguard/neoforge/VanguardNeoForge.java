package net.vanguard.neoforge;

import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.vanguard.Vanguard;
import net.vanguard.debug.VanguardDebugEntry;

@Mod(value = Vanguard.MOD_ID, dist = Dist.CLIENT)
public final class VanguardNeoForge {
    public VanguardNeoForge(IEventBus modEventBus) {
        Vanguard.init();
        modEventBus.addListener(VanguardNeoForge::registerDebugEntries);
    }

    private static void registerDebugEntries(RegisterDebugEntriesEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(Vanguard.MOD_ID, "entity_culling");
        event.register(id, new VanguardDebugEntry());
        event.includeInProfile(id, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
        event.includeInProfile(id, DebugScreenProfile.PERFORMANCE, DebugScreenEntryStatus.IN_OVERLAY);
    }
}
