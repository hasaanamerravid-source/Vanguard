package net.vanguard.mixin;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.vanguard.Vanguard;
import net.vanguard.debug.VanguardDebugEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(DebugScreenEntries.class)
public abstract class DebugScreenEntriesMixin {
    @Shadow
    @Final
    private static Map<Identifier, DebugScreenEntry> ENTRIES_BY_ID;

    @Shadow
    @Final
    @Mutable
    public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void vanguard$registerDebugEntry(CallbackInfo ci) {
        Identifier id = Identifier.fromNamespaceAndPath(Vanguard.MOD_ID, "entity_culling");
        ENTRIES_BY_ID.put(id, new VanguardDebugEntry());

        Map<Identifier, DebugScreenEntryStatus> defaultProfile =
                new HashMap<>(PROFILES.get(DebugScreenProfile.DEFAULT));
        defaultProfile.put(id, DebugScreenEntryStatus.IN_OVERLAY);

        Map<Identifier, DebugScreenEntryStatus> performanceProfile =
                new HashMap<>(PROFILES.get(DebugScreenProfile.PERFORMANCE));
        performanceProfile.put(id, DebugScreenEntryStatus.IN_OVERLAY);

        PROFILES = Map.of(
                DebugScreenProfile.DEFAULT, Map.copyOf(defaultProfile),
                DebugScreenProfile.PERFORMANCE, Map.copyOf(performanceProfile)
        );
    }
}
