package dev.local.copycatlight.client;

import java.util.BitSet;
import java.util.Objects;

/** Immutable six-neighbor exterior classification. Glass can seal the enclosure independently of light transmission. */
public final class EnclosureGrid {
    public static final int MAX_VOLUME = 262144;
    private final int minX, minY, minZ, sx, sy, sz, plane;
    private final BitSet sealed;
    private final BitSet exterior;

    private EnclosureGrid(int minX, int minY, int minZ, int sx, int sy, int sz,
                          BitSet sealed, BitSet exterior) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.sx = sx; this.sy = sy; this.sz = sz; this.plane = sx * sy;
        this.sealed = sealed; this.exterior = exterior;
    }

    public static EnclosureGrid build(int minX, int minY, int minZ, int sx, int sy, int sz, boolean[] cells) {
        Objects.requireNonNull(cells, "sealed cells");
        // Validate each factor before multiplication: three arbitrary int dimensions could overflow a long.
        if (sx < 1 || sy < 1 || sz < 1 || sx > MAX_VOLUME || sy > MAX_VOLUME || sz > MAX_VOLUME)
            throw new IllegalArgumentException("Grid dimensions must be positive and bounded");
        long volume = (long) sx * sy * sz;
        if (volume > MAX_VOLUME || cells.length != volume)
            throw new IllegalArgumentException("Grid volume exceeds limit or does not match cell array");
        int count = (int) volume;
        int plane = sx * sy;
        var sealed = new BitSet(count);
        var exterior = new BitSet(count);
        var queue = new int[count];
        int head = 0, tail = 0;
        for (int i = 0; i < count; i++) if (cells[i]) sealed.set(i);
        for (int z = 0; z < sz; z++) for (int y = 0; y < sy; y++) for (int x = 0; x < sx; x++) {
            if (x != 0 && x != sx - 1 && y != 0 && y != sy - 1 && z != 0 && z != sz - 1) continue;
            int i = x + y * sx + z * plane;
            if (!sealed.get(i)) { exterior.set(i); queue[tail++] = i; }
        }
        while (head < tail) {
            int i = queue[head++];
            int x = i % sx, y = i / sx % sy, z = i / plane;
            if (x > 0) tail = visit(i - 1, sealed, exterior, queue, tail);
            if (x + 1 < sx) tail = visit(i + 1, sealed, exterior, queue, tail);
            if (y > 0) tail = visit(i - sx, sealed, exterior, queue, tail);
            if (y + 1 < sy) tail = visit(i + sx, sealed, exterior, queue, tail);
            if (z > 0) tail = visit(i - plane, sealed, exterior, queue, tail);
            if (z + 1 < sz) tail = visit(i + plane, sealed, exterior, queue, tail);
        }
        return new EnclosureGrid(minX, minY, minZ, sx, sy, sz, sealed, exterior);
    }

    private static int visit(int i, BitSet sealed, BitSet exterior, int[] queue, int tail) {
        if (!sealed.get(i) && !exterior.get(i)) {
            exterior.set(i); queue[tail++] = i;
        }
        return tail;
    }

    public boolean outside(int worldX, int worldY, int worldZ) {
        int i = index(worldX, worldY, worldZ);
        return i < 0 || exterior.get(i);
    }

    public boolean isSealed(int worldX, int worldY, int worldZ) {
        int i = index(worldX, worldY, worldZ);
        return i >= 0 && sealed.get(i);
    }

    private int index(int worldX, int worldY, int worldZ) {
        long x = (long) worldX - minX, y = (long) worldY - minY, z = (long) worldZ - minZ;
        if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) return -1;
        return (int) (x + y * sx + z * plane);
    }
}
