package dev.local.copycatlight.mixin;

import com.copycatsplus.copycats.utility.BlockEntityUtils;
import dev.local.copycatlight.MaterialLighting;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityUtils.class)
abstract class MaterialLightUpdateMixin {
    @Inject(method = "updateLight", at = @At("TAIL"), remap = false)
    private static void copycatlight$refresh(BlockEntity entity, CallbackInfo ci) {
        MaterialLighting.refresh(entity);
    }
}
