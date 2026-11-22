package dev.local.copycatlight.mixin.client;

import dev.local.copycatlight.RenderInvalidation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class SurfaceBlockChangeMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void copycatlight$geometryChanged(BlockPos pos, BlockState state, boolean moving,
                                              CallbackInfoReturnable<BlockState> cir) {
        var previous = cir.getReturnValue();
        var level = ((LevelChunk)(Object)this).getLevel();
        if (previous != null && level.isClientSide
                && RenderInvalidation.seals(previous, level, pos) != RenderInvalidation.seals(state, level, pos))
            RenderInvalidation.changed(level, pos);
    }
}
