package dev.local.copycatlight.mixin;

import dev.local.copycatlight.MaterialLighting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateLightMixin {
    @Inject(method = "getLightBlock", at = @At("HEAD"), cancellable = true)
    private void copycatlight$opacity(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        BlockState state = (BlockState) (Object) this;
        if (MaterialLighting.isCopycat(state)) cir.setReturnValue(MaterialLighting.lightBlock(state, level, pos));
    }

    @Inject(method = "propagatesSkylightDown", at = @At("HEAD"), cancellable = true)
    private void copycatlight$sky(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = (BlockState) (Object) this;
        if (MaterialLighting.isCopycat(state)) cir.setReturnValue(MaterialLighting.lightBlock(state, level, pos) == 0);
    }

    @Inject(method = "useShapeForLightOcclusion", at = @At("HEAD"), cancellable = true)
    private void copycatlight$shape(CallbackInfoReturnable<Boolean> cir) {
        if (MaterialLighting.isCopycat((BlockState) (Object) this)) cir.setReturnValue(false);
    }
}
