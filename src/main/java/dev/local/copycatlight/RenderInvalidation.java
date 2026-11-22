package dev.local.copycatlight;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Installed by the client; no client classes are resolved on a dedicated server. */
public final class RenderInvalidation {
    public static BiConsumer<Level, BlockPos> listener = (level, pos) -> {};
    private RenderInvalidation() {}
    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide) listener.accept(level, pos);
    }
    public static boolean seals(BlockState state, BlockGetter level, BlockPos pos) {
        return MaterialLighting.isCopycat(state) || !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }
}
