package dev.local.copycatlight;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Real Sable plot lighting; no physics simulation or user save required. */
public final class SableLightingSmokeTests {
    private static int assertions;
    public static void verify(ServerLevel level) {
        var container = SubLevelContainer.getContainer(level);
        var body = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
        try {
            var plot = body.getPlot();
            var center = plot.getCenterBlock();
            plot.newEmptyChunk(new ChunkPos(center));
            var origin = new BlockPos((center.getX() & ~15) + 3, 100, (center.getZ() & ~15) + 3);
            var gate = origin.offset(3, 1, 1);
            for (var p : BlockPos.betweenClosed(origin, origin.offset(6, 2, 2)))
                level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
            for (int x = 1; x <= 5; x++) level.setBlockAndUpdate(origin.offset(x, 1, 1), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(1, 1, 1), Blocks.GLOWSTONE.defaultBlockState());
            level.setBlockAndUpdate(gate, CCBlocks.COPYCAT_BLOCK.getDefaultState());
            var engine = plot.getLightEngine();
            fill(level, gate, Blocks.STONE.defaultBlockState()); drain(engine);
            require(brightness(engine, gate.east()) == 0, "opaque plot wall blocks interior light");
            fill(level, gate, Blocks.GLASS.defaultBlockState());
            require(engine.hasLightWork(), "material edit queues the plot engine");
            drain(engine);
            require(brightness(engine, gate.east()) == 12, "glass transmits in actual Sable plot");
            fill(level, gate, Blocks.STONE.defaultBlockState()); drain(engine);
            require(brightness(engine, gate.east()) == 0, "glass-to-stone removes existing light");
            fill(level, gate, Blocks.RED_STAINED_GLASS.defaultBlockState()); drain(engine);
            require(brightness(engine, gate.east()) == 12, "stained glass transmits");
            fill(level, gate, Blocks.TINTED_GLASS.defaultBlockState()); drain(engine);
            require(brightness(engine, gate.east()) == 0, "tinted glass blocks");
            fill(level, gate, Blocks.GLASS.defaultBlockState()); drain(engine);
            var entity = level.getBlockEntity(gate);
            var saved = entity.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(gate);
            var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(gate, level.getBlockState(gate), saved, level.registryAccess());
            level.setBlockEntity(restored); restored.onLoad(); drain(engine);
            require(level.getBlockState(gate).getLightBlock(level, gate) == 0, "plot entity reload restores opacity");
            require(brightness(engine, gate.east()) == 12, "plot entity reload restores transmission");
            checkClosedShell(level, origin.offset(0, 6, 0), engine);
            System.out.println("COPYCAT SABLE LIGHT SMOKE PASSED: " + assertions + " assertions; actual independent plot engine at " + gate + ".");
        } finally {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
    }

    private static void checkClosedShell(ServerLevel level, BlockPos origin, LevelLightEngine engine) {
        for (int x = 0; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 4; z++) {
            var pos = origin.offset(x, y, z);
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(pos, wall ? CCBlocks.COPYCAT_BLOCK.getDefaultState() : Blocks.AIR.defaultBlockState());
            if (wall) fill(level, pos, Blocks.STONE.defaultBlockState());
        }
        var center = origin.offset(2, 2, 2);
        level.setBlockAndUpdate(center, Blocks.GLOWSTONE.defaultBlockState()); drain(engine);
        for (var face : net.minecraft.core.Direction.values())
            require(brightness(engine, center.relative(face, 3)) == 0, "closed Copycats shell exterior dark: " + face);
        var window = center.east(2);
        fill(level, window, Blocks.GLASS.defaultBlockState()); drain(engine);
        require(brightness(engine, window.east()) == 12, "only glass window transmits shell interior light");
        fill(level, window, Blocks.STONE.defaultBlockState()); drain(engine);
        for (var face : net.minecraft.core.Direction.values())
            require(brightness(engine, center.relative(face, 3)) == 0, "reclosed shell clears external light: " + face);
    }

    private static void fill(ServerLevel level, BlockPos pos, BlockState material) {
        var entity = (ICopycatBlockEntity) level.getBlockEntity(pos);
        entity.setMaterial(material);
        entity.setConsumedItem(new net.minecraft.world.item.ItemStack(material.getBlock()));
    }
    private static int brightness(LevelLightEngine engine, BlockPos pos) {
        return engine.getLayerListener(LightLayer.BLOCK).getLightValue(pos);
    }
    private static void drain(LevelLightEngine engine) {
        int count = 0;
        while (engine.hasLightWork()) {
            if (++count > 100) throw new AssertionError("plot lighting did not settle");
            engine.runLightUpdates();
        }
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
