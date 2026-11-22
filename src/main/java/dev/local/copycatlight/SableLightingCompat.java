package dev.local.copycatlight;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded only when Sable is present. Plot light is independent of world light. */
final class SableLightingCompat {
    static boolean refresh(Level level, BlockPos pos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        var plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
        if (plot == null) return false;
        var chunk = container.getChunk(new net.minecraft.world.level.ChunkPos(pos));
        if (chunk != null) {
            chunk.getSkyLightSources().update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            plot.getLightEngine().checkBlock(pos);
        }
        return true;
    }
}
