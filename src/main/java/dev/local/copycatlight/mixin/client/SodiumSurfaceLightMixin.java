package dev.local.copycatlight.mixin.client;

import dev.local.copycatlight.client.SurfaceLighting;
import dev.local.copycatlight.MaterialLighting;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.QuadViewImpl;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Sodium 0.8 world renderer counterpart; its AO/normal shade stays intact. */
@Pseudo
@Mixin(targets = {
        "net.caffeinemc.mods.sodium.client.model.light.flat.FlatLightPipeline",
        "net.caffeinemc.mods.sodium.client.model.light.smooth.SmoothLightPipeline"
}, remap = false)
abstract class SodiumSurfaceLightMixin {
    @Shadow(remap = false) @Final private LightDataAccess lightCache;

    @Inject(method = "calculate", at = @At("TAIL"), remap = false)
    private void copycatlight$surfaceLight(ModelQuadView quad, BlockPos pos, QuadLightData out,
            Direction cullFace, Direction lightFace, boolean shade, boolean enhanced, CallbackInfo ci) {
        var level = lightCache.getLevel();
        // Evaluate this before resolving the sprite: ordinary world quads must
        // never invoke optional sprite accessors or enter the repair path.
        if (!MaterialLighting.isCopycat(level.getBlockState(pos))) return;
        TextureAtlasSprite sprite;
        if (quad instanceof MutableQuadViewImpl mutable) {
            // FRAPI's inherited getSprite() deliberately throws. The cached
            // sprite preserves Copycats' part wrapper when fromVanilla supplies it.
            sprite = mutable.cachedSprite();
        } else if (quad instanceof QuadViewImpl) {
            sprite = null;
        } else {
            sprite = quad.getSprite();
        }
        SurfaceLighting.repairSodium(level, pos, lightFace, sprite, out.lm);
    }
}
