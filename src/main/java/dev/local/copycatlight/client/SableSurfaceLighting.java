package dev.local.copycatlight.client;

import dev.local.copycatlight.MaterialLighting;
import dev.local.copycatlight.RenderInvalidation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Optional Sable client adapter. All world/pose sampling happens on the main thread. */
public final class SableSurfaceLighting {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int GEOMETRY_BUDGET = 4096;
    private static final int ENVIRONMENT_BUDGET = 512;
    private static final Map<LevelPlot, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Set<LevelPlot> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<LevelPlot, Bounds> TOO_LARGE = new HashMap<>();
    private static ClientLevel currentLevel;
    private static GeometryJob geometry;
    private static EnvironmentJob environment;
    private static final ArrayDeque<LevelPlot> environmentQueue = new ArrayDeque<>();
    private static int ticks;

    private SableSurfaceLighting() {}

    public static void changed(Level level, BlockPos pos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        var plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
        if (plot != null) DIRTY.add(plot);
    }

    public static void chunkChanged(Level level, ChunkPos pos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        var plot = container.getPlot(pos.x, pos.z);
        if (plot != null) DIRTY.add(plot);
    }

    public static int[] adjust(BlockAndTintGetter level, BlockState state, BlockPos pos,
                               BakedQuad quad, int[] lights) {
        if (!(level instanceof SubLevelContainerHolder holder)) return null;
        var container = holder.sable$getPlotContainer();
        if (container == null) return null;
        var plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
        if (plot == null) return null;
        var snapshot = SNAPSHOTS.get(plot);
        if (snapshot == null) return null;
        var profile = snapshot.profiles.get(pos.asLong());
        if (profile == null || profile.block() != state.getBlock()) return null;

        // Each part keeps its own transparency, even in a mixed opaque/glass block.
        if (SurfaceLighting.transparent(profile, quad)) {
            var result = SurfaceLighting.repairFace(level, pos, quad, lights, profile);
            return SurfaceLighting.atLeast(result, packed(level, pos));
        }
        var direction = quad.getDirection();
        var sample = faceSample(snapshot.grid, pos, direction);
        if (snapshot.grid.isSealed(sample.getX(), sample.getY(), sample.getZ()))
            return SurfaceLighting.repairFace(level, pos, quad, lights, profile);
        int emission = SurfaceLighting.emission(profile, quad, level, pos) << 4;
        if (!snapshot.grid.outside(sample.getX(), sample.getY(), sample.getZ())) {
            return SurfaceLighting.atLeast(lights, SurfaceLighting.max(packed(level, sample), emission));
        }
        var external = snapshot.external.get(pos.asLong());
        if (external == null) return null;
        // Replace plot light on exterior faces: leaked cabin light has no source identity.
        int light = SurfaceLighting.max(external[direction.ordinal()], emission);
        int[] result = lights.clone();
        int surfaceSky = packed(level, sample) & 0xFFFF0000;
        for (int i = 0; i < result.length; i++)
            // Sable scales plot sky light at draw time. Preserve that sky channel;
            // applying a world sky sample here would multiply the shadow twice.
            result[i] = (SurfaceLighting.max(result[i], surfaceSky) & 0xFFFF0000) | (light & 0xFFFF);
        return result;
    }

    private static int packed(BlockAndTintGetter level, BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos) << 20;
    }

    private static BlockPos faceSample(EnclosureGrid grid, BlockPos pos, Direction direction) {
        // Do not raycast through solid cells: a thick wall is not exterior air.
        return pos.relative(direction);
    }

    public static void tick(ClientLevel level) {
        if (currentLevel != level) {
            SNAPSHOTS.clear(); DIRTY.clear(); TOO_LARGE.clear(); geometry = null; environment = null;
            environmentQueue.clear(); currentLevel = level; ticks = 0;
        }
        if (level == null) return;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        var live = new HashSet<LevelPlot>();
        for (var body : container.getAllSubLevels()) {
            var plot = body.getPlot(); live.add(plot);
            var snapshot = SNAPSHOTS.get(plot);
            var bounds = plot.getBoundingBox();
            var rejected = TOO_LARGE.get(plot);
            if (rejected != null && rejected.matches(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ())) { DIRTY.remove(plot); continue; }
            TOO_LARGE.remove(plot);
            if ((geometry == null || geometry.plot != plot) && (snapshot == null || !snapshot.sameBounds(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()))) DIRTY.add(plot);
        }
        SNAPSHOTS.keySet().retainAll(live); DIRTY.retainAll(live); TOO_LARGE.keySet().retainAll(live);
        if (geometry != null && !live.contains(geometry.plot)) geometry = null;
        if (environment != null && !live.contains(environment.plot)) environment = null;
        if (geometry == null && !DIRTY.isEmpty()) {
            var plot = DIRTY.iterator().next(); DIRTY.remove(plot);
            geometry = GeometryJob.create(plot);
            // Ignore excessive bounds rather than scanning an unbounded volume each frame.
            if (geometry == null) {
                var b = plot.getBoundingBox();
                TOO_LARGE.put(plot, new Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                if (SNAPSHOTS.remove(plot) != null && plot.getSubLevel() instanceof ClientSubLevel body
                        && body.getRenderData() != null) body.getRenderData().rebuild();
            }
        }
        if (geometry != null && geometry.step(level)) {
            var result = geometry.finish();
            if (result != null) {
                SNAPSHOTS.put(geometry.plot, result);
                environmentQueue.remove(geometry.plot);
                environmentQueue.addFirst(geometry.plot);
            }
            geometry = null;
        }
        if (++ticks % 10 == 0 && environmentQueue.isEmpty()) environmentQueue.addAll(SNAPSHOTS.keySet());
        if (environment == null) {
            while (!environmentQueue.isEmpty()) {
                var plot = environmentQueue.removeFirst();
                var snapshot = SNAPSHOTS.get(plot);
                if (live.contains(plot) && snapshot != null && !snapshot.profiles.isEmpty()
                        && plot.getSubLevel() instanceof ClientSubLevel body) {
                    environment = new EnvironmentJob(plot, body, snapshot); break;
                }
            }
        }
        if (environment != null && environment.step(level)) {
            if (SNAPSHOTS.get(environment.plot) == environment.source) {
                var result = environment.finish();
                SNAPSHOTS.put(environment.plot, result);
                // Only sections whose external lighting changed need a new mesh.
                var data = environment.body.getRenderData();
                if (data != null) {
                    if (environment.source.external.isEmpty()) data.rebuild();
                    else for (long section : environment.changedSections) {
                        var p = BlockPos.of(section);
                        data.setDirty(p.getX(), p.getY(), p.getZ(), false);
                    }
                }
            }
            environment = null;
        }
    }

    private record Snapshot(EnclosureGrid grid, Map<Long, MaterialLighting.Profile> profiles,
                            Map<Long, int[]> external, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean sameBounds(int ax, int ay, int az, int bx, int by, int bz) {
            return minX == ax && minY == ay && minZ == az && maxX == bx && maxY == by && maxZ == bz;
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean matches(int ax, int ay, int az, int bx, int by, int bz) {
            return minX == ax && minY == ay && minZ == az && maxX == bx && maxY == by && maxZ == bz;
        }
    }

    private static final class GeometryJob {
        final LevelPlot plot;
        final int minX, minY, minZ, maxX, maxY, maxZ, sx, sy, sz;
        final boolean[] sealed;
        final Map<Long, MaterialLighting.Profile> profiles = new HashMap<>();
        int cursor;
        private GeometryJob(LevelPlot plot, int ax, int ay, int az, int bx, int by, int bz) {
            this.plot = plot; minX = ax; minY = ay; minZ = az; maxX = bx; maxY = by; maxZ = bz;
            sx = bx - ax + 3; sy = by - ay + 3; sz = bz - az + 3;
            sealed = new boolean[sx * sy * sz];
        }
        static GeometryJob create(LevelPlot plot) {
            var b = plot.getBoundingBox();
            long sx = (long)b.maxX() - b.minX() + 3, sy = (long)b.maxY() - b.minY() + 3, sz = (long)b.maxZ() - b.minZ() + 3;
            if (sx < 3 || sy < 3 || sz < 3 || sx > 262144 || sy > 262144 || sz > 262144
                    || sx * sy * sz > 262144) return null;
            return new GeometryJob(plot, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        }
        boolean step(ClientLevel level) {
            int end = Math.min(cursor + GEOMETRY_BUDGET, sealed.length);
            var pos = new BlockPos.MutableBlockPos();
            while (cursor < end) {
                int i = cursor++, x = i % sx, y = i / sx % sy, z = i / (sx * sy);
                if (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1) continue;
                pos.set(minX - 1 + x, minY - 1 + y, minZ - 1 + z);
                var state = level.getBlockState(pos);
                if (MaterialLighting.isCopycat(state)) {
                    sealed[i] = true;
                    var profile = MaterialLighting.cachedProfile(level, pos);
                    if (profile != null) profiles.put(pos.asLong(), profile);
                } else sealed[i] = RenderInvalidation.seals(state, level, pos);
            }
            return cursor == sealed.length;
        }
        Snapshot finish() {
            return new Snapshot(EnclosureGrid.build(minX - 1, minY - 1, minZ - 1, sx, sy, sz, sealed),
                    Map.copyOf(profiles), Map.of(), minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static final class EnvironmentJob {
        final LevelPlot plot;
        final ClientSubLevel body;
        final Snapshot source;
        final Long[] positions;
        final Map<Long, int[]> external = new HashMap<>();
        final Set<Long> changedSections = new HashSet<>();
        final dev.ryanhcode.sable.companion.math.Pose3d pose;
        int cursor;
        EnvironmentJob(LevelPlot plot, ClientSubLevel body, Snapshot source) {
            this.plot = plot; this.body = body; this.source = source;
            pose = new dev.ryanhcode.sable.companion.math.Pose3d(body.logicalPose());
            positions = source.profiles.keySet().toArray(Long[]::new);
        }
        boolean step(ClientLevel level) {
            int end = Math.min(cursor + ENVIRONMENT_BUDGET / 6, positions.length);
            var global = new Vector3d();
            while (cursor < end) {
                long key = positions[cursor++]; var pos = BlockPos.of(key);
                int[] lights = new int[6];
                for (var direction : DIRECTIONS) {
                    var localSample = faceSample(source.grid, pos, direction);
                    if (source.grid.isSealed(localSample.getX(), localSample.getY(), localSample.getZ())
                            || !source.grid.outside(localSample.getX(), localSample.getY(), localSample.getZ())) continue;
                    global.set(pos.getX() + .5 + direction.getStepX() * .6,
                            pos.getY() + .5 + direction.getStepY() * .6,
                            pos.getZ() + .5 + direction.getStepZ() * .6);
                    pose.transformPosition(global);
                    var sample = BlockPos.containing(global.x, global.y, global.z);
                    if (!level.hasChunkAt(sample)) {
                        var previous = source.external.get(key);
                        lights[direction.ordinal()] = previous == null ? 0 : previous[direction.ordinal()];
                    } else lights[direction.ordinal()] = level.getBrightness(LightLayer.BLOCK, sample) << 4;
                }
                external.put(key, lights);
                if (!java.util.Arrays.equals(lights, source.external.get(key)))
                    changedSections.add(new BlockPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4).asLong());
            }
            return cursor == positions.length;
        }
        Snapshot finish() {
            return new Snapshot(source.grid, source.profiles, Map.copyOf(external),
                    source.minX, source.minY, source.minZ, source.maxX, source.maxY, source.maxZ);
        }
    }
}
