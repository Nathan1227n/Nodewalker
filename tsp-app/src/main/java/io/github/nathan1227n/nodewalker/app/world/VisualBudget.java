package io.github.nathan1227n.nodewalker.app.world;

/** Admission estimates, not heap reservations, mathematical N limits or GPU guarantees. */
public final class VisualBudget {
    public static final long MAX_ESTIMATED_BYTES = 64L * 1024 * 1024;
    public static final long MAX_ESTIMATED_TRIANGLES = 160_000;
    private VisualBudget() { }
    public static long estimatedTriangles(int n) { return Math.addExact(60_000L, Math.multiplyExact(1_200L, n)); }
    public static long estimatedBytes(int n) { return Math.addExact(16L * 1024 * 1024, Math.multiplyExact(400_000L, n)); }
    public static void requireAllowed(int n) {
        if (n < 3) throw new IllegalArgumentException("N deve ser >= 3");
        if (estimatedTriangles(n) > MAX_ESTIMATED_TRIANGLES || estimatedBytes(n) > MAX_ESTIMATED_BYTES)
            throw new IllegalArgumentException("Orçamento visual excedido: estimativa de " + estimatedTriangles(n)
                    + " triângulos / " + estimatedBytes(n) + " bytes. Não é um limite matemático do solver.");
    }
}
