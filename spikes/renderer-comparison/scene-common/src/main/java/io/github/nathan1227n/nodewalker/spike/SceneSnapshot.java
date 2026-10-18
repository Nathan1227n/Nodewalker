package io.github.nathan1227n.nodewalker.spike;

import java.util.List;

/** Fixed, immutable visual fixture. No solver execution or optimality claim. */
public final class SceneSnapshot {
    public static final String ID = "refuges-v1";
    public static final String NOTICE = "REFERENCIA VISUAL | sem busca / sem certificado de otimo";
    private static final List<Vec3> CITIES = List.of(
            new Vec3(160, 190, 220), new Vec3(720, 240, 140), new Vec3(860, 170, 690),
            new Vec3(490, 120, 850), new Vec3(210, 270, 590));
    // Stored rounded Euclidean costs from the logical coordinates, never from the display mesh.
    private static final long[][] COSTS = {
            {0, 568, 843, 715, 382}, {568, 0, 572, 756, 681}, {843, 572, 0, 406, 665},
            {715, 756, 406, 0, 410}, {382, 681, 665, 410, 0}};
    private static final List<Integer> CYCLE = List.of(0, 1, 2, 3, 4, 0);
    private SceneSnapshot() { }
    public static List<Vec3> cities() { return CITIES; }
    public static List<Integer> cycle() { return CYCLE; }
    public static long cost(int i, int j) { return COSTS[i][j]; }
    public static long[][] matrix() { return java.util.Arrays.stream(COSTS).map(long[]::clone).toArray(long[][]::new); }
    public static long cycleCost() {
        long cost = 0;
        for (int i = 1; i < CYCLE.size(); i++) cost = Math.addExact(cost, COSTS[CYCLE.get(i - 1)][CYCLE.get(i)]);
        return cost;
    }
    public static String describe(int index) {
        Vec3 p = CITIES.get(index);
        return String.format(java.util.Locale.ROOT, "%s  |  XYZ logicos (%.0f, %.0f, %.0f)",
                index == 0 ? "BASE 0" : "REFUGIO " + index, p.x(), p.y(), p.z());
    }
}
