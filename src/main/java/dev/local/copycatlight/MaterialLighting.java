package dev.local.copycatlight;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Reads existing material data. No extra block states or persistent data are added. */
public final class MaterialLighting {
    private static final boolean HAS_SABLE = net.neoforged.fml.loading.LoadingModList.get().getModFileById("sable") != null;
    private MaterialLighting() {}

    public static boolean isCopycat(BlockState state) {
        return state.getBlock() instanceof ICopycatBlock;
    }

    public static int lightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        // Level.getBlockEntity deliberately returns null on the lighting thread.
        // Publish immutable material values on edits/load instead of accessing BEs there.
        if (level instanceof Level world) return cached(state, world, pos);
        if (level instanceof ImposterProtoChunk imposter) return lightBlock(state, imposter.getWrapped(), pos);
        if (level instanceof LevelChunk chunk) {
            // Vanilla sky-column scans pass chunk-relative x/z positions.
            BlockPos worldPos = new BlockPos(chunk.getPos().getMinBlockX() + (pos.getX() & 15),
                    pos.getY(), chunk.getPos().getMinBlockZ() + (pos.getZ() & 15));
            return cached(state, chunk.getLevel(), worldPos);
        }
        // Render/contraption snapshots provide their own safe BlockGetter access.
        var entity = level.getBlockEntity(pos);
        return entity == null ? 15 : profile(entity, level, pos).opacity(state);
    }

    public static Profile cachedProfile(Level level, BlockPos pos) {
        var chunk = ((LightCacheAccess) level).copycatlight$profiles().get(ChunkPos.asLong(pos));
        return chunk == null ? null : chunk.get(pos.asLong());
    }

    /** Render regions expose a snapshot; avoid consulting a live Level BE off-thread. */
    public static Profile renderProfile(BlockGetter level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity == null ? null : profile(entity, level, pos);
    }

    private static int cached(BlockState state, Level level, BlockPos pos) {
        var value = cachedProfile(level, pos);
        return value == null ? 15 : value.opacity(state);
    }

    public static boolean publish(BlockEntity entity) {
        var level = entity.getLevel();
        if (level == null) return false;
        var pos = entity.getBlockPos();
        var value = profile(entity, level, pos);
        var previous = ((LightCacheAccess) level).copycatlight$profiles()
                .computeIfAbsent(ChunkPos.asLong(pos), ignored -> new ConcurrentHashMap<>())
                .put(pos.asLong(), value);
        return !value.equals(previous);
    }

    public static void refresh(BlockEntity entity) {
        var level = entity.getLevel();
        if (level == null || entity.isRemoved() || !isCopycat(entity.getBlockState())) return;
        if (level instanceof net.createmod.catnip.levelWrappers.SchematicLevel) return;
        boolean changed = publish(entity);
        var pos = entity.getBlockPos();
        if (changed) RenderInvalidation.changed(level, pos);
        if (HAS_SABLE && SableLightingCompat.refresh(level, pos)) return;
        // Updating emission alone does not refresh a column's cached sky sources.
        // Never create or load a chunk just to refresh one material.
        var lightChunk = level.getChunkSource().getChunkForLighting(pos.getX() >> 4, pos.getZ() >> 4);
        if (lightChunk instanceof ImposterProtoChunk imposter) lightChunk = imposter.getWrapped();
        if (lightChunk instanceof LevelChunk chunk) {
            chunk.getSkyLightSources().update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            level.getChunkSource().getLightEngine().checkBlock(pos);
        }
    }

    private static Profile profile(BlockEntity entity, BlockGetter level, BlockPos pos) {
        var state = entity.getBlockState();
        if (state.getBlock() instanceof IMultiStateCopycatBlock block
                && entity instanceof IMultiStateCopycatBlockEntity multi) {
            var parts = new HashMap<String, Integer>();
            var storage = multi.getMaterialItemStorage();
            var materials = new HashMap<String, BlockState>();
            if (storage == null) return new Profile(state.getBlock(), 15, Map.of(), null, Map.of());
            for (String property : block.storageProperties()) {
                var part = storage.getMaterialItem(property);
                parts.put(property, materialOpacity(part == null ? null : part.material(), level, pos));
                if (part != null && part.material() != null) materials.put(property, part.material());
            }
            return new Profile(state.getBlock(), 15, Map.copyOf(parts), null, Map.copyOf(materials));
        }
        if (entity instanceof ICopycatBlockEntity copycat)
            return new Profile(state.getBlock(), materialOpacity(copycat.getMaterial(), level, pos), Map.of(), copycat.getMaterial(), Map.of());
        return new Profile(state.getBlock(), 15, Map.of(), null, Map.of());
    }

    public record Profile(Block block, int singleOpacity, Map<String, Integer> parts,
                          BlockState singleMaterial, Map<String, BlockState> materials) {
        public BlockState material(String property) {
            return materials.isEmpty() ? singleMaterial : materials.get(property);
        }
        public int opacity(BlockState state) {
            if (state.getBlock() != block) return 15;
            if (!(block instanceof IMultiStateCopycatBlock multi) || parts.isEmpty()) return singleOpacity;
            int opacity = 0;
            boolean found = false;
            for (var part : parts.entrySet()) {
                if (!multi.partExists(state, part.getKey())) continue;
                found = true;
                opacity = Math.max(opacity, part.getValue());
                if (opacity == 15) break;
            }
            return found ? opacity : 15;
        }
    }

    private static int materialOpacity(BlockState material, BlockGetter level, BlockPos pos) {
        if (material == null || material.isAir() || isCopycat(material) || AllBlocks.COPYCAT_BASE.has(material))
            return 15;
        return Math.clamp(material.getLightBlock(level, pos), 0, 15);
    }
}
