package io.github.nathan1227n.nodewalker.core.shortestpath;

/**
 * Explicit cooperative budgets. K permits K operations; only attempting the next hits the limit.
 * Memory is an estimate for solver workspace/snapshot, not actual heap or graph storage.
 */
public record ShortestPathExecutionOptions(long maxProcessedVertices, long maxExaminedEdges,
                                           long maxElapsedMillis, long maxEstimatedBytes) {
    public static final long DEFAULT_MAX_ESTIMATED_BYTES = 64L * 1024 * 1024;

    public ShortestPathExecutionOptions {
        if (maxProcessedVertices < 0 || maxExaminedEdges < 0 || maxElapsedMillis < 0
                || maxElapsedMillis > Long.MAX_VALUE / 1_000_000 || maxEstimatedBytes <= 0) {
            throw new IllegalArgumentException("Invalid shortest-path budgets or nonrepresentable duration.");
        }
    }

    /** Initial demonstration limits, not a measured performance guarantee. */
    public static ShortestPathExecutionOptions demonstration() {
        return limits(10_000, 1_000_000, 2000);
    }

    public static ShortestPathExecutionOptions limits(long vertices, long edges, long millis) {
        return new ShortestPathExecutionOptions(vertices, edges, millis, DEFAULT_MAX_ESTIMATED_BYTES);
    }

    public ShortestPathExecutionOptions withMemoryBudget(long bytes) {
        return new ShortestPathExecutionOptions(maxProcessedVertices, maxExaminedEdges, maxElapsedMillis, bytes);
    }
}
