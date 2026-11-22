package dev.local.copycatlight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.local.copycatlight.client.SurfaceLighting;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** AO and flat rendering share this exit; retain the existing vertex consumer wrappers. */
@Mixin(ModelBlockRenderer.class)
abstract class CopycatSurfaceLightMixin {
    @WrapOperation(method = "putQuadData", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void copycatlight$surfaceLight(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha,
            int[] lightmaps, int overlay, boolean readExistingColor, Operation<Void> original,
            @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BlockState state,
            @Local(argsOnly = true) BlockPos pos) {
        original.call(consumer, pose, quad, brightness, red, green, blue, alpha,
                SurfaceLighting.adjust(level, state, pos, quad, lightmaps), overlay, readExistingColor);
    }
}
