package dev.local.copycatlight;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateTextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Actual transformed ModelBlockRenderer tests, without connecting to a world. */
@EventBusSubscriber(modid = "copycat_light_control", value = Dist.CLIENT)
public final class ClientLightingSmokeTests {
    private static final BlockPos ORIGIN = new BlockPos(0, 100, 0);
    private static boolean finished;
    private static int assertions;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("copycat_light_control.clientSmoke")) return;
        var client = Minecraft.getInstance();
        if (!client.isGameLoadFinished()) return;
        finished = true;
        try {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "test window remains hidden");
            var renderer = client.getBlockRenderer().getModelRenderer();
            var stone = Blocks.STONE.defaultBlockState();
            var vanillaModel = client.getBlockRenderer().getBlockModel(stone);
            var random = RandomSource.create(42);
            var shapes = List.of(CCBlocks.COPYCAT_BLOCK.getDefaultState(), CCBlocks.COPYCAT_SLAB.getDefaultState(),
                    CCBlocks.COPYCAT_STAIRS.getDefaultState());
            for (var state : shapes) {
                var entity = ((EntityBlock) state.getBlock()).newBlockEntity(ORIGIN, state);
                require(entity instanceof ICopycatBlockEntity, "copycat test shape has material entity");
                for (var material : List.of(stone, Blocks.GLASS.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState(),
                        Blocks.GLOWSTONE.defaultBlockState())) {
                    ((ICopycatBlockEntity) entity).setMaterialInternal(material);
                    String partProperty = null;
                    if (state.getBlock() instanceof IMultiStateCopycatBlock parts
                            && entity instanceof IMultiStateCopycatBlockEntity multi) {
                        for (String property : parts.storageProperties()) {
                            multi.getMaterialItemStorage().getMaterialItem(property).setMaterial(material);
                            if (parts.partExists(state, property)) partProperty = property;
                        }
                    }
                    var level = new FaceWorld(state, entity);
                    for (var face : Direction.values()) {
                        var source = vanillaModel.getQuads(stone, face, random).getFirst();
                        var vertices = source.getVertices().clone();
                        // Put the quad inside its own cell, reproducing thin slab/stair sampling.
                        for (int v = 0; v < 4; v++) {
                            int axis = face.getAxis().ordinal();
                            vertices[v * 8 + axis] = Float.floatToRawIntBits(0.5F);
                        }
                        var sprite = partProperty == null ? source.getSprite()
                                : new MultiStateTextureAtlasSprite(partProperty, source.getSprite());
                        var quad = new BakedQuad(vertices, -1, face, sprite, true);
                        var model = new OneQuad(quad);
                        for (boolean ao : new boolean[]{false, true}) {
                            var output = new Capture();
                            if (ao) renderer.tesselateWithAO(level, model, state, ORIGIN,
                                    new PoseStack(), output, false, random, 42, 0, ModelData.EMPTY, null);
                            else renderer.tesselateWithoutAO(level, model, state, ORIGIN,
                                    new PoseStack(), output, false, random, 42, 0, ModelData.EMPTY, null);
                            require(output.lights.size() == 1, "one quad traverses " + (ao ? "AO" : "flat") + " path");
                            for (int packed : output.lights.getFirst()) {
                                require((packed & 0xFFFF) >= 11 << 4, "inset face keeps neighboring block light: " + face);
                                require((packed >>> 16) >= 13 << 4, "inset face keeps neighboring sky light: " + face);
                                if (material.is(Blocks.GLOWSTONE))
                                    require((packed & 0xFFFF) == 15 << 4, "emissive material remains luminous: state=" + state
                                            + " face=" + face + " ao=" + ao + " light=" + packed
                                            + " profile=" + MaterialLighting.renderProfile(level, ORIGIN));
                            }
                            for (float brightness : output.brightness.getFirst())
                                require(Float.isFinite(brightness) && brightness >= 0 && brightness <= 1,
                                        "AO/shading remains valid");
                        }
                    }
                }
            }
            // Control: an ordinary inset vanilla face must not be brightened by this patch.
            var source = vanillaModel.getQuads(stone, Direction.UP, random).getFirst();
            var vertices = source.getVertices().clone();
            for (int v = 0; v < 4; v++) vertices[v * 8 + 1] = Float.floatToRawIntBits(0.5F);
            var control = new OneQuad(new BakedQuad(vertices, -1, Direction.UP, source.getSprite(), true));
            var capture = new Capture();
            var ordinarySlab = Blocks.OAK_SLAB.defaultBlockState();
            renderer.tesselateWithoutAO(new FaceWorld(ordinarySlab, null), control, ordinarySlab, ORIGIN,
                    new PoseStack(), capture, false, random, 42, 0, ModelData.EMPTY, null);
            for (int packed : capture.lights.getFirst()) require(packed == 0, "ordinary vanilla model remains unchanged");
            if (net.neoforged.fml.loading.LoadingModList.get().getModFileById("sable") != null)
                Class.forName("dev.local.copycatlight.SableSurfaceSmokeTests").getMethod("verify").invoke(null);
            if (Boolean.getBoolean("copycat_light_control.sodiumSmoke")) {
                System.out.println("SODIUM TEST DETECTION loading=" + net.neoforged.fml.loading.LoadingModList.get().getMods().stream().map(mod -> mod.getModId()).toList()
                        + " runtime=" + net.neoforged.fml.ModList.get().getMods().stream().map(mod -> mod.getModId()).toList());
                Class.forName("dev.local.copycatlight.SodiumLightingSmokeTests").getMethod("verify").invoke(null);
            }
            System.out.println("COPYCAT CLIENT LIGHT SMOKE PASSED: " + assertions
                    + " assertions; actual transformed vanilla flat/AO renderer, all six inset faces, opaque/glass/stained glass, hidden window.");
            client.stop();
        } catch (Throwable error) {
            System.err.println("COPYCAT CLIENT LIGHT SMOKE FAILED");
            error.printStackTrace();
            client.stop();
        }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private record OneQuad(BakedQuad quad) implements BakedModel {
        public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
            return face == null ? List.of(quad) : List.of();
        }
        public boolean useAmbientOcclusion() { return true; }
        public boolean isGui3d() { return false; }
        public boolean usesBlockLight() { return true; }
        public boolean isCustomRenderer() { return false; }
        public TextureAtlasSprite getParticleIcon() { return quad.getSprite(); }
        public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private record FaceWorld(BlockState state, BlockEntity entity) implements BlockAndTintGetter {
        public float getShade(Direction direction, boolean shade) { return shade ? 0.8F : 1; }
        public LevelLightEngine getLightEngine() { return null; }
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return pos.equals(ORIGIN) ? 0 : layer == LightLayer.BLOCK ? 11 : 13;
        }
        public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xFFFFFF; }
        public BlockEntity getBlockEntity(BlockPos pos) { return pos.equals(ORIGIN) ? entity : null; }
        public BlockState getBlockState(BlockPos pos) { return pos.equals(ORIGIN) ? state : Blocks.AIR.defaultBlockState(); }
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }

    private static final class Capture implements VertexConsumer {
        final List<int[]> lights = new ArrayList<>();
        final List<float[]> brightness = new ArrayList<>();
        @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                float red, float green, float blue, float alpha, int[] lightmap, int overlay, boolean readAlpha) {
            this.lights.add(lightmap.clone()); this.brightness.add(brightness.clone());
        }
        public VertexConsumer addVertex(float x, float y, float z) { return this; }
        public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
