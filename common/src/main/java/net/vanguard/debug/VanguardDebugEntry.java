package net.vanguard.debug;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.vanguard.culling.EntityCuller;

/**
 * F3 line registered into the vanilla debug screen. The values are reset at
 * the start of each level extraction, so the line describes the frame that is
 * currently being displayed.
 */
public final class VanguardDebugEntry implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer displayer, Level clientLevel, LevelChunk clientChunk, LevelChunk serverChunk) {
        EntityCuller culler = EntityCuller.get();
        if (!culler.isDebugLineEnabled()) {
            return;
        }
        displayer.addLine(culler.debugLine());
    }
}
