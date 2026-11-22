package dev.local.copycatlight.client;

import java.util.Arrays;

public final class EnclosureGridTest {
    private static int assertions;
    public static void main(String[] args) {
        boolean[] shell = new boolean[125];
        for (int z = 1; z <= 3; z++) for (int y = 1; y <= 3; y++) for (int x = 1; x <= 3; x++)
            shell[index(x, y, z, 5, 5)] = x == 1 || x == 3 || y == 1 || y == 3 || z == 1 || z == 3;
        var closed = EnclosureGrid.build(-20, 80, -100, 5, 5, 5, shell);
        require(!closed.outside(-18, 82, -98), "closed shell contains an interior cell");
        require(closed.outside(-20, 80, -100), "air padding is exterior");
        require(closed.isSealed(-19, 82, -98), "wall location is sealed");
        require(!closed.isSealed(-18, 82, -98), "interior air is not sealed");
        require(closed.outside(-21, 82, -98), "outside grid is exterior");
        require(!closed.isSealed(-21, 82, -98), "outside grid is not sealed");
        // A glass window remains a topology seal; no opacity knowledge is embedded in the grid.
        shell[index(1, 2, 2, 5, 5)] = true;
        var glass = EnclosureGrid.build(-20, 80, -100, 5, 5, 5, shell);
        require(!glass.outside(-18, 82, -98), "glass-sealed window retains interior classification");
        shell[index(1, 2, 2, 5, 5)] = false;
        var open = EnclosureGrid.build(-20, 80, -100, 5, 5, 5, shell);
        require(open.outside(-18, 82, -98), "one door hole connects interior to exterior");
        require(!glass.outside(-18, 82, -98), "input mutation cannot modify published snapshot");
        Arrays.fill(shell, true);
        require(open.outside(-18, 82, -98), "all input mutations remain isolated");
        var singleAir = EnclosureGrid.build(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 1, 1, 1, new boolean[1]);
        require(singleAir.outside(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), "single air is boundary exterior");
        require(singleAir.outside(Integer.MAX_VALUE, 0, Integer.MIN_VALUE), "coordinate subtraction cannot wrap into bounds");
        var singleSolid = EnclosureGrid.build(0, 0, 0, 1, 1, 1, new boolean[]{true});
        require(singleSolid.isSealed(0, 0, 0), "single solid stays sealed");
        require(!singleSolid.outside(0, 0, 0), "sealed cells are not traversable exterior");
        var max = EnclosureGrid.build(0, 0, 0, 64, 64, 64, new boolean[EnclosureGrid.MAX_VOLUME]);
        require(max.outside(32, 32, 32), "maximum volume empty grid fills completely");
        invalid(() -> EnclosureGrid.build(0, 0, 0, 0, 1, 1, new boolean[0]), "zero dimension");
        invalid(() -> EnclosureGrid.build(0, 0, 0, -1, 1, 1, new boolean[0]), "negative dimension");
        invalid(() -> EnclosureGrid.build(0, 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, new boolean[0]), "overflow dimensions");
        invalid(() -> EnclosureGrid.build(0, 0, 0, 65, 65, 65, new boolean[0]), "excessive volume");
        invalid(() -> EnclosureGrid.build(0, 0, 0, 2, 2, 2, new boolean[7]), "array mismatch");
        require(EnclosureGrid.build(0, 0, 0, 1, 4, 1, new boolean[4]).outside(0, 2, 0), "one-cell-thick grid boundaries");
        System.out.println("COPYCAT ENCLOSURE GRID PASSED: " + assertions + " assertions.");
    }
    private static int index(int x, int y, int z, int sx, int sy) { return x + y * sx + z * sx * sy; }
    private static void require(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void invalid(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Accepted invalid input: " + message);
    }
}
