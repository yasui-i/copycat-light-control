package dev.local.copycatlight;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated development-world tests, excluded from release jars. */
@EventBusSubscriber(modid = "copycat_light_control")
public final class LightingSmokeTests {
    private static final BlockPos GATE = new BlockPos(3, 101, 0);
    private static final BlockPos SKY = new BlockPos(-27, 105, -27);
    private static int ticks;
    private static int assertions;
    private static boolean active;

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("copycat_light_control.smoke")) return;
        try {
            var level = event.getServer().overworld();
            if (Boolean.getBoolean("copycat_light_control.sableSmoke")) {
                Class.forName("dev.local.copycatlight.SableLightingSmokeTests")
                    .getMethod("verify", ServerLevel.class).invoke(null, level);
                event.getServer().halt(false);
                return;
            }
            level.setChunkForced(0, 0, true);
            level.setChunkForced(-2, -2, true);
            level.setChunkForced(2, 0, true);
            if (Boolean.getBoolean("copycat_light_control.reloadSmoke")) {
                level.getChunkAt(GATE);
                level.getChunkAt(SKY);
                active = true;
                return;
            }
            level.setDayTime(6000);
            for (var p : BlockPos.betweenClosed(0, 100, -1, 6, 102, 1))
                level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
            for (int x = 1; x <= 5; x++) level.setBlockAndUpdate(new BlockPos(x, 101, 0), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(1, 101, 0), Blocks.GLOWSTONE.defaultBlockState());
            for (var p : BlockPos.betweenClosed(SKY.offset(-1, -10, -1), SKY.offset(1, 0, 1)))
                level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
            for (int y = 96; y < 105; y++) level.setBlockAndUpdate(new BlockPos(SKY.getX(), y, SKY.getZ()), Blocks.AIR.defaultBlockState());
            place(level, GATE, CCBlocks.COPYCAT_BLOCK.getDefaultState(), Blocks.STONE.defaultBlockState());
            place(level, SKY, CCBlocks.COPYCAT_BLOCK.getDefaultState(), Blocks.STONE.defaultBlockState());
            checkAllShapes(level);
            active = true;
        } catch (Throwable t) { fail(event.getServer(), t); }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!active || ++ticks % 40 != 0) return;
        var level = event.getServer().overworld();
        try {
            if (Boolean.getBoolean("copycat_light_control.reloadSmoke")) {
                require(level.getBlockState(GATE).getLightBlock(level, GATE) == 15, "opaque profile rebuilt on server restart");
                require(level.getBlockState(SKY).getLightBlock(level, SKY) == 0, "glass profile rebuilt on server restart");
                require(level.getBrightness(LightLayer.SKY, SKY.below()) == 15, "negative-coordinate glass skylight after restart");
                require(level.getBrightness(LightLayer.BLOCK, GATE.east()) == 14, "emissive material after restart");
                System.out.println("COPYCAT LIGHT RELOAD SMOKE PASSED: " + assertions + " assertions; existing save profiles, negative coordinates, sky light and emission.");
                active = false;
                event.getServer().halt(false);
                return;
            }
            switch (ticks) {
                case 40 -> {
                    checkLight(level, 0, 0, "stone");
                    fill(level, GATE, Blocks.GLASS.defaultBlockState());
                    fill(level, SKY, Blocks.GLASS.defaultBlockState());
                }
                case 80 -> {
                    checkLight(level, 12, 15, "glass replacement");
                    fill(level, GATE, Blocks.RED_STAINED_GLASS.defaultBlockState());
                    fill(level, SKY, Blocks.RED_STAINED_GLASS.defaultBlockState());
                }
                case 120 -> {
                    checkLight(level, 12, 15, "stained glass");
                    fill(level, GATE, Blocks.TINTED_GLASS.defaultBlockState());
                    fill(level, SKY, Blocks.TINTED_GLASS.defaultBlockState());
                }
                case 160 -> {
                    checkLight(level, 0, 0, "tinted glass");
                    fill(level, GATE, AllBlocks.COPYCAT_BASE.getDefaultState());
                    fill(level, SKY, AllBlocks.COPYCAT_BASE.getDefaultState());
                }
                case 200 -> {
                    checkLight(level, 0, 0, "removed material");
                    fill(level, GATE, Blocks.GLASS.defaultBlockState());
                    fill(level, SKY, Blocks.GLASS.defaultBlockState());
                }
                case 240 -> {
                    checkLight(level, 12, 15, "glass restored");
                    // Save and load through Copycats' existing data path.
                    var entity = level.getBlockEntity(GATE);
                    var saved = entity.saveWithFullMetadata(level.registryAccess());
                    level.removeBlockEntity(GATE);
                    var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(GATE,
                            level.getBlockState(GATE), saved, level.registryAccess());
                    level.setBlockEntity(restored);
                    restored.onLoad();
                    require(((ICopycatBlockEntity) restored).getMaterial().is(Blocks.GLASS), "material NBT unchanged");
                }
                case 280 -> {
                    checkLight(level, 12, 15, "entity reload");
                    fill(level, GATE, Blocks.GLOWSTONE.defaultBlockState());
                }
                case 320 -> {
                    require(level.getBrightness(LightLayer.BLOCK, GATE.east()) == 14, "emissive material preserved");
                    System.out.println("COPYCAT LIGHT SMOKE PASSED: " + assertions + " assertions; live block/sky light, all registered shapes, multi-material, base Create, emission, material removal and NBT reload.");
                    active = false;
                    event.getServer().halt(false);
                }
            }
        } catch (Throwable t) { fail(event.getServer(), t); }
    }

    private static void checkAllShapes(ServerLevel level) {
        var pos = new BlockPos(40, 100, 0);
        int shapes = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("copycats") || id.getPath().equals("wrapped_copycat")
                    || !MaterialLighting.isCopycat(block.defaultBlockState())) continue;
            var candidate = block.defaultBlockState();
            if (block instanceof IMultiStateCopycatBlock multi)
                candidate = block.getStateDefinition().getPossibleStates().stream()
                        .filter(s -> multi.storageProperties().stream().anyMatch(p -> multi.partExists(s, p)))
                        .findFirst().orElse(candidate);
            final var state = candidate;
            level.setBlock(pos, state, 2);
            if (!(level.getBlockEntity(pos) instanceof ICopycatBlockEntity)) continue;
            fill(level, pos, Blocks.STONE.defaultBlockState());
            require(state.getLightBlock(level, pos) == 15, id + " stone opaque");
            require(!state.propagatesSkylightDown(level, pos), id + " stone sky opaque");
            fill(level, pos, Blocks.GLASS.defaultBlockState());
            require(state.getLightBlock(level, pos) == 0, id + " glass transparent");
            require(!state.useShapeForLightOcclusion(), id + " transparent shape does not occlude");
            if (block instanceof IMultiStateCopycatBlock multi && level.getBlockEntity(pos) instanceof IMultiStateCopycatBlockEntity be) {
                String existing = multi.storageProperties().stream().filter(p -> multi.partExists(state, p)).findFirst().orElse(null);
                if (existing != null) {
                    be.setMaterial(existing, Blocks.STONE.defaultBlockState());
                    require(state.getLightBlock(level, pos) == 15, id + " mixed material opaque");
                    be.setMaterial(existing, Blocks.GLASS.defaultBlockState());
                    for (String p : multi.storageProperties()) if (!multi.partExists(state, p))
                        be.setMaterial(p, Blocks.STONE.defaultBlockState());
                    require(state.getLightBlock(level, pos) == 0, id + " absent parts ignored");
                }
            }
            shapes++;
            level.removeBlock(pos, false);
        }
        for (var block : new net.minecraft.world.level.block.Block[] { AllBlocks.COPYCAT_PANEL.get(), AllBlocks.COPYCAT_STEP.get() }) {
            place(level, pos, block.defaultBlockState(), Blocks.STONE.defaultBlockState());
            require(level.getBlockState(pos).getLightBlock(level, pos) == 15, "base Create stone opaque");
            fill(level, pos, Blocks.GLASS.defaultBlockState());
            require(level.getBlockState(pos).getLightBlock(level, pos) == 0, "base Create glass transparent");
            level.removeBlock(pos, false);
        }
        require(Blocks.STONE.defaultBlockState().getLightBlock(level, pos) == 15, "ordinary stone unchanged");
        require(Blocks.GLASS.defaultBlockState().getLightBlock(level, pos) == 0, "ordinary glass unchanged");
        System.out.println("COPYCAT LIGHT SHAPES CHECKED: " + shapes);
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, BlockState material) {
        level.setBlockAndUpdate(pos, state);
        fill(level, pos, material);
    }

    private static void fill(ServerLevel level, BlockPos pos, BlockState material) {
        var be = (ICopycatBlockEntity) level.getBlockEntity(pos);
        if (be instanceof IMultiStateCopycatBlockEntity multi) {
            for (String p : ((IMultiStateCopycatBlock) level.getBlockState(pos).getBlock()).storageProperties()) {
                multi.setMaterial(p, material);
                multi.setConsumedItem(p, new net.minecraft.world.item.ItemStack(material.getBlock()));
            }
        } else {
            be.setMaterial(material);
            be.setConsumedItem(new net.minecraft.world.item.ItemStack(material.getBlock()));
        }
    }

    private static void checkLight(ServerLevel level, int block, int sky, String label) {
        int actualBlock = level.getBrightness(LightLayer.BLOCK, GATE.east());
        int actualSky = level.getBrightness(LightLayer.SKY, SKY.below());
        require(actualBlock == block, label + " block light: " + actualBlock + " expected " + block);
        require(actualSky == sky, label + " sky light: " + actualSky + " expected " + sky);
        System.out.println("COPYCAT LIGHT PHASE: " + label + " block=" + actualBlock + " sky=" + actualSky);
    }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void fail(net.minecraft.server.MinecraftServer server, Throwable t) {
        System.err.println("COPYCAT LIGHT SMOKE FAILED");
        t.printStackTrace();
        active = false;
        server.halt(false);
    }
}
