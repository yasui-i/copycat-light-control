package dev.local.copycatlight;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateTextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.local.copycatlight.client.EnclosureGrid;
import dev.local.copycatlight.client.SableSurfaceLighting;
import dev.local.copycatlight.client.SurfaceLighting;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Real transformed face-rendering tests with deterministic Sable enclosure snapshots. */
public final class SableSurfaceSmokeTests {
    private static final BlockPos WALL = new BlockPos(1, 2, 2);
    private static int assertions;

    @SuppressWarnings("unchecked")
    public static void verify() throws Exception {
        var state = CCBlocks.COPYCAT_BLOCK.getDefaultState();
        var container = new FakeContainer();
        var world = new FaceWorld(state, container);
        var snapshotType = Class.forName("dev.local.copycatlight.client.SableSurfaceLighting$Snapshot");
        var constructor = snapshotType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var snapshotsField = SableSurfaceLighting.class.getDeclaredField("SNAPSHOTS");
        snapshotsField.setAccessible(true);
        var snapshots = (Map<LevelPlot, Object>) snapshotsField.get(null);
        boolean[] sealed = new boolean[125];
        for (int x = 1; x <= 3; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 3; z++)
            sealed[x + y * 5 + z * 25] = x == 1 || x == 3 || y == 1 || y == 3 || z == 1 || z == 3;
        var grid = EnclosureGrid.build(0, 0, 0, 5, 5, 5, sealed);
        int[] exterior = new int[]{32, 32, 32, 32, 32, 32};
        var stone = Blocks.STONE.defaultBlockState();
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(stone);
        try {
            for (var material : List.of(stone, Blocks.GLASS.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState())) {
                int opacity = material.is(Blocks.GLASS) ? 0 : 15;
                var profile = new MaterialLighting.Profile(state.getBlock(), opacity, Map.of(), material, Map.of());
                snapshots.put(container.plot, constructor.newInstance(grid, Map.of(WALL.asLong(), profile),
                        Map.of(WALL.asLong(), exterior), 1, 1, 1, 3, 3, 3));
                for (var face : List.of(Direction.EAST, Direction.WEST)) {
                    var quad = model.getQuads(stone, face, RandomSource.create(42)).getFirst();
                    int expected = material.is(Blocks.GLOWSTONE) ? 15 : material.is(Blocks.GLASS) || face == Direction.EAST ? 11 : 2;
                    for (boolean ao : new boolean[]{false, true}) {
                        var capture = render(world, state, quad, ao);
                        require(capture.size() == 1, "one Sable face rendered");
                        for (int packed : capture.getFirst()) {
                            require((packed & 0xFFFF) == expected << 4,
                                    "Sable face block light: material=" + material + " face=" + face + " ao=" + ao + " light=" + packed);
                            require(packed >>> 16 == 13 << 4, "Sable plot sky channel preserved");
                        }
                    }
                }
            }
            // A glass part must not inherit opacity from its stone neighbor in a mixed block.
            var mixed = new MaterialLighting.Profile(state.getBlock(), 15, Map.of("glass", 0, "stone", 15), null,
                    Map.of("glass", Blocks.GLASS.defaultBlockState(), "stone", stone));
            snapshots.put(container.plot, constructor.newInstance(grid, Map.of(WALL.asLong(), mixed),
                    Map.of(WALL.asLong(), exterior), 1, 1, 1, 3, 3, 3));
            var source = model.getQuads(stone, Direction.WEST, RandomSource.create(42)).getFirst();
            for (var part : List.of("glass", "stone")) {
                var quad = new BakedQuad(source.getVertices().clone(), -1, Direction.WEST,
                        new MultiStateTextureAtlasSprite(part, source.getSprite()), true);
                var light = SurfaceLighting.adjust(world, state, WALL, quad, new int[]{11 << 4 | 13 << 20, 11 << 4 | 13 << 20, 11 << 4 | 13 << 20, 11 << 4 | 13 << 20});
                for (int packed : light) require((packed & 0xFFFF) == (part.equals("glass") ? 11 : 2) << 4,
                        "mixed material part opacity remains independent");
            }
            System.out.println("COPYCAT SABLE SURFACE SMOKE PASSED: " + assertions
                    + " assertions; actual transformed flat/AO face pipeline, interior/exterior, world block light, glass, emission, mixed parts.");
        } finally { snapshots.remove(container.plot); }
    }

    private static List<int[]> render(FaceWorld world, BlockState state, BakedQuad quad, boolean ao) {
        var capture = new Capture();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var random = RandomSource.create(42);
        if (ao) renderer.tesselateWithAO(world, new OneQuad(quad), state, WALL, new PoseStack(), capture,
                false, random, 42, 0, ModelData.EMPTY, null);
        else renderer.tesselateWithoutAO(world, new OneQuad(quad), state, WALL, new PoseStack(), capture,
                false, random, 42, 0, ModelData.EMPTY, null);
        return capture.lights;
    }

    private static void require(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }

    private static final class FakeContainer extends SubLevelContainer {
        final LevelPlot plot;
        FakeContainer() {
            super(null, 1, 1, 0, 0);
            plot = new LevelPlot(this, 0, 0, 1, null) {
                public LevelLightEngine getLightEngine() { return null; }
                protected void onRemoveChunkHolder(LevelChunk chunk) {}
            };
        }
        public LevelPlot getPlot(int x, int z) { return plot; }
        protected SubLevel createSubLevel(int x, int z, Pose3d pose, UUID uuid) { return null; }
    }

    private record FaceWorld(BlockState state, FakeContainer container) implements BlockAndTintGetter, SubLevelContainerHolder {
        public SubLevelContainer sable$getPlotContainer() { return container; }
        public float getShade(Direction face, boolean shade) { return shade ? .8F : 1; }
        public LevelLightEngine getLightEngine() { return null; }
        public int getBrightness(LightLayer layer, BlockPos pos) { return layer == LightLayer.BLOCK ? 11 : 13; }
        public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xFFFFFF; }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) { return pos.equals(WALL) ? state : Blocks.AIR.defaultBlockState(); }
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }

    private record OneQuad(BakedQuad quad) implements BakedModel {
        public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) { return face == null ? List.of(quad) : List.of(); }
        public boolean useAmbientOcclusion() { return true; }
        public boolean isGui3d() { return false; }
        public boolean usesBlockLight() { return true; }
        public boolean isCustomRenderer() { return false; }
        public TextureAtlasSprite getParticleIcon() { return quad.getSprite(); }
        public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static final class Capture implements VertexConsumer {
        final List<int[]> lights = new ArrayList<>();
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                float red, float green, float blue, float alpha, int[] lightmap, int overlay, boolean readAlpha) { lights.add(lightmap.clone()); }
        public VertexConsumer addVertex(float x, float y, float z) { return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
