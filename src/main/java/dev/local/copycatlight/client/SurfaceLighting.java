package dev.local.copycatlight.client;

import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateTextureAtlasSprite;
import dev.local.copycatlight.MaterialLighting;
import dev.local.copycatlight.RenderLevelAccess;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.LoadingModList;

/** Client-only face lighting. Does not change AO, tint, geometry or light propagation. */
public final class SurfaceLighting {
    private static final boolean HAS_SABLE = LoadingModList.get().getModFileById("sable") != null;
    private SurfaceLighting() {}

    public static int[] adjust(BlockAndTintGetter level, BlockState state, BlockPos pos,
                               BakedQuad quad, int[] lights) {
        if (!MaterialLighting.isCopycat(state)) return lights;
        if (HAS_SABLE) {
            int[] result = SableSurfaceLighting.adjust(level, state, pos, quad, lights);
            if (result != null) return result;
        }
        return repairFace(level, pos, quad, lights, profile(level, pos));
    }

    private static MaterialLighting.Profile profile(BlockAndTintGetter level, BlockPos pos) {
        return level instanceof RenderLevelAccess region ? MaterialLighting.cachedProfile(region.copycatlight$level(), pos)
                : level instanceof Level world ? MaterialLighting.cachedProfile(world, pos)
                : MaterialLighting.renderProfile(level, pos);
    }

    static String property(BakedQuad quad) {
        return quad.getSprite() instanceof MultiStateTextureAtlasSprite sprite ? sprite.getProperty() : "material";
    }

    static int emission(MaterialLighting.Profile profile, BakedQuad quad, BlockAndTintGetter level, BlockPos pos) {
        var material = profile == null ? null : profile.material(property(quad));
        return material == null ? 0 : material.getLightEmission(level, pos);
    }

    static boolean transparent(MaterialLighting.Profile profile, BakedQuad quad) {
        if (profile == null) return false;
        return profile.parts().isEmpty() ? profile.singleOpacity() < 15
                : profile.parts().getOrDefault(property(quad), 15) < 15;
    }

    static int[] repairFace(BlockAndTintGetter level, BlockPos pos, BakedQuad quad, int[] lights,
                            MaterialLighting.Profile profile) {
        var sample = pos.relative(quad.getDirection());
        int block = Math.max(level.getBrightness(LightLayer.BLOCK, sample), emission(profile, quad, level, pos));
        int sky = level.getBrightness(LightLayer.SKY, sample);
        if (transparent(profile, quad)) {
            // A transparent part in a mixed-material cell must also see the lit
            // side of the window when the opaque part blocks its own cell.
            var opposite = pos.relative(quad.getDirection().getOpposite());
            block = Math.max(block, Math.max(level.getBrightness(LightLayer.BLOCK, pos),
                    level.getBrightness(LightLayer.BLOCK, opposite)));
            sky = Math.max(sky, Math.max(level.getBrightness(LightLayer.SKY, pos),
                    level.getBrightness(LightLayer.SKY, opposite)));
        }
        return atLeast(lights, block << 4 | sky << 20);
    }

    /** Sodium's world renderer bypasses ModelBlockRenderer; preserve its AO output. */
    public static void repairSodium(BlockAndTintGetter level, BlockPos pos, Direction face,
                                    TextureAtlasSprite sprite, int[] lights) {
        if (!MaterialLighting.isCopycat(level.getBlockState(pos))) return;
        var sample = pos.relative(face);
        int sampled = level.getBrightness(LightLayer.BLOCK, sample) << 4
                | level.getBrightness(LightLayer.SKY, sample) << 20;
        var profile = profile(level, pos);
        if (profile != null) {
            String property = sprite instanceof MultiStateTextureAtlasSprite multi ? multi.getProperty() : "material";
            int opacity = profile.parts().isEmpty() ? profile.singleOpacity() : profile.parts().getOrDefault(property, 15);
            var material = profile.material(property);
            if (material != null) sampled = max(sampled, material.getLightEmission(level, pos) << 4);
            if (opacity < 15) {
                var opposite = pos.relative(face.getOpposite());
                sampled = max(sampled, level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos) << 20);
                sampled = max(sampled, level.getBrightness(LightLayer.BLOCK, opposite) << 4 | level.getBrightness(LightLayer.SKY, opposite) << 20);
            }
        }
        for (int i = 0; i < lights.length; i++) lights[i] = max(lights[i], sampled);
    }

    static int[] atLeast(int[] lights, int sampled) {
        int[] result = lights;
        for (int i = 0; i < lights.length; i++) {
            int corrected = max(lights[i], sampled);
            if (corrected != lights[i]) {
                if (result == lights) result = lights.clone();
                result[i] = corrected;
            }
        }
        return result;
    }

    static int max(int a, int b) {
        return Math.max(a & 0xFFFF, b & 0xFFFF) | Math.max(a >>> 16, b >>> 16) << 16;
    }
}
