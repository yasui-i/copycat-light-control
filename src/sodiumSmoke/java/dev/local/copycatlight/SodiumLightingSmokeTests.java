package dev.local.copycatlight;

import com.copycatsplus.copycats.CCBlocks;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.light.flat.FlatLightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuad;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.EncodingFormat;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Exercises the transformed Sodium calculate methods, not just the repair helper. */
public final class SodiumLightingSmokeTests {
    private static final BlockPos ORIGIN = new BlockPos(0, 100, 0);
    private static int assertions;

    public static void verify() {
        for (boolean smooth : new boolean[]{false, true}) {
            for (Direction face : Direction.values()) {
                var quad = new ModelQuad();
                quad.setFlags(2); // Parallel and inset: vanilla Sodium samples the dark source cell.
                quad.setLightFace(face);
                for (int v = 0; v < 4; v++) {
                    quad.setX(v, face.getAxis() == Direction.Axis.X ? .5F : (v & 1));
                    quad.setY(v, face.getAxis() == Direction.Axis.Y ? .5F : (v >>> 1));
                    quad.setZ(v, face.getAxis() == Direction.Axis.Z ? .5F : ((v ^ (v >>> 1)) & 1));
                }
                var copycatWorld = new FaceWorld(CCBlocks.COPYCAT_SLAB.getDefaultState());
                var ordinaryWorld = new FaceWorld(Blocks.OAK_SLAB.defaultBlockState());
                var copycat = calculate(smooth, copycatWorld, quad, face);
                var ordinary = calculate(smooth, ordinaryWorld, quad, face);
                for (int v = 0; v < 4; v++) {
                    require((copycat.lm[v] & 0xFFFF) >= (11 << 4), "copycat neighboring block light: " + smooth + "/" + face);
                    require((copycat.lm[v] >>> 16) >= (13 << 4), "copycat neighboring sky light: " + smooth + "/" + face);
                    require(Float.isFinite(copycat.br[v]) && Math.abs(copycat.br[v] - ordinary.br[v]) < 0.00001F,
                            "Sodium AO and direction shade remain unchanged");
                    if (!smooth) require(ordinary.lm[v] == 0, "ordinary flat inset remains zero");
                    else require((ordinary.lm[v] & 0xFFFF) < (11 << 4), "ordinary smooth inset keeps source/neighbor blend");
                }
            }
        }
        verifyFrapi();
        System.out.println("COPYCAT SODIUM LIGHT SMOKE PASSED: " + assertions
                + " assertions; actual transformed Sodium flat/smooth pipelines, six inset faces, vanilla control and real FRAPI QuadViewImpl crash regression.");
    }

    private static void verifyFrapi() {
        try {
            for (boolean mutable : new boolean[]{false, true})
                for (boolean smooth : new boolean[]{false, true}) for (Direction face : Direction.values()) {
                // This is the exact Sodium implementation in the user's stack trace,
                // including its deliberately unsupported ModelQuadView.getSprite().
                var quad = frapiQuad(face, mutable);
                boolean unsupported = false;
                try { quad.getSprite(); } catch (UnsupportedOperationException expected) { unsupported = true; }
                require(unsupported, "real FRAPI quad reproduces unsupported sprite accessor");
                var copycat = calculate(smooth, new FaceWorld(CCBlocks.COPYCAT_SLAB.getDefaultState()), quad, face);
                var ordinary = calculate(smooth, new FaceWorld(Blocks.BEDROCK.defaultBlockState()), quad, face);
                for (int v = 0; v < 4; v++) {
                    require((copycat.lm[v] & 0xFFFF) >= 11 << 4, "FRAPI copycat receives neighbor block light: " + smooth + "/" + face);
                    require(copycat.lm[v] >>> 16 >= 13 << 4, "FRAPI copycat receives neighbor sky light: " + smooth + "/" + face);
                    require(Float.isFinite(copycat.br[v]) && Math.abs(copycat.br[v] - ordinary.br[v]) < .00001F,
                            "FRAPI AO/directional brightness remains unchanged");
                    if (!smooth) require(ordinary.lm[v] == 0, "ordinary FRAPI bedrock remains unmodified");
                    else require((ordinary.lm[v] & 0xFFFF) < 11 << 4, "ordinary FRAPI source/neighbor blend remains unmodified");
                }
                // Ordinary models must be filtered before any optional sprite lookup.
                var poison = new UnreadableSpriteQuad();
                poison.setFlags(2); poison.setLightFace(face);
                for (int v = 0; v < 4; v++) {
                    poison.setX(v, quad.getX(v)); poison.setY(v, quad.getY(v)); poison.setZ(v, quad.getZ(v));
                }
                calculate(smooth, new FaceWorld(Blocks.BEDROCK.defaultBlockState()), poison, face);
                require(poison.spriteReads == 0, "ordinary block is rejected before sprite access");
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot construct real Sodium FRAPI regression fixture", error);
        }
    }

    private static ModelQuadView frapiQuad(Direction face, boolean mutable) throws ReflectiveOperationException {
        var quadType = Class.forName("net.caffeinemc.mods.sodium.client.render.frapi.mesh.QuadViewImpl");
        var encoding = Class.forName("net.caffeinemc.mods.sodium.client.render.frapi.mesh.EncodingFormat");
        var quad = mutable ? new MutableFixture() : quadType.getConstructor().newInstance();
        int[] data = new int[constant(encoding, "TOTAL_STRIDE")];
        int stride = constant(encoding, "VERTEX_STRIDE");
        int xOffset = constant(encoding, "VERTEX_X"), yOffset = constant(encoding, "VERTEX_Y"), zOffset = constant(encoding, "VERTEX_Z");
        for (int v = 0; v < 4; v++) {
            data[v * stride + xOffset] = Float.floatToRawIntBits(face.getAxis() == Direction.Axis.X ? .5F : (v & 1));
            data[v * stride + yOffset] = Float.floatToRawIntBits(face.getAxis() == Direction.Axis.Y ? .5F : (v >>> 1));
            data[v * stride + zOffset] = Float.floatToRawIntBits(face.getAxis() == Direction.Axis.Z ? .5F : ((v ^ (v >>> 1)) & 1));
        }
        var setFlags = encoding.getDeclaredMethod("geometryFlags", int.class, int.class); setFlags.setAccessible(true);
        var setLightFace = encoding.getDeclaredMethod("lightFace", int.class, Direction.class); setLightFace.setAccessible(true);
        data[0] = (int) setFlags.invoke(null, 0, 2);
        data[0] = (int) setLightFace.invoke(null, data[0], face);
        var dataField = quadType.getDeclaredField("data"); dataField.setAccessible(true); dataField.set(quad, data);
        var invalid = quadType.getDeclaredField("isGeometryInvalid"); invalid.setAccessible(true); invalid.setBoolean(quad, false);
        return (ModelQuadView) quad;
    }

    private static int constant(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.getInt(null);
    }

    private static final class UnreadableSpriteQuad extends ModelQuad {
        int spriteReads;
        @Override public TextureAtlasSprite getSprite() {
            spriteReads++;
            throw new AssertionError("ordinary block sprite must not be queried");
        }
    }

    /** The production FRAPI context uses this mutable subclass; sprite lookup is still unsupported. */
    private static final class MutableFixture extends MutableQuadViewImpl {
        MutableFixture() { data = new int[EncodingFormat.TOTAL_STRIDE]; }
        @Override public void emitDirectly() {}
    }

    private static QuadLightData calculate(boolean smooth, FaceWorld world, ModelQuadView quad, Direction face) {
        var cache = new TestCache(world, face);
        LightPipeline pipeline = smooth ? new SmoothLightPipeline(cache) : new FlatLightPipeline(cache);
        var out = new QuadLightData();
        pipeline.calculate(quad, ORIGIN, out, null, face, true, false);
        return out;
    }

    private static final class TestCache extends LightDataAccess {
        private final Direction face;
        TestCache(FaceWorld world, Direction face) { this.level = world; this.face = face; }
        @Override public int get(int x, int y, int z) {
            // An opaque wall occupies the source plane; all samples on its exposed side are lit.
            boolean source = (x - ORIGIN.getX()) * face.getStepX()
                    + (y - ORIGIN.getY()) * face.getStepY() + (z - ORIGIN.getZ()) * face.getStepZ() == 0;
            return packBL(source ? 0 : 11) | packSL(source ? 0 : 13) | packAO(1.0F);
        }
    }

    private record FaceWorld(BlockState state) implements BlockAndTintGetter {
        public float getShade(Direction direction, boolean shade) { return shade ? .8F : 1; }
        public LevelLightEngine getLightEngine() { return null; }
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return pos.equals(ORIGIN) ? 0 : layer == LightLayer.BLOCK ? 11 : 13;
        }
        public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xFFFFFF; }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) { return pos.equals(ORIGIN) ? state : Blocks.AIR.defaultBlockState(); }
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
