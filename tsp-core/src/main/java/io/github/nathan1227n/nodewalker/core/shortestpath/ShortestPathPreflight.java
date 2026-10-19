package io.github.nathan1227n.nodewalker.core.shortestpath;

import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Metadata only. Estimates are neither heap measurements nor reservations. */
public record ShortestPathPreflight(int vertexCount, long undirectedEdges,
                                    BigInteger solverEstimatedBytes, BigInteger topologyEstimatedBytes,
                                    BigInteger matrixPayloadBytes, ShortestPathExecutionOptions options,
                                    boolean executionAllowed, List<String> warnings) {
    public ShortestPathPreflight {
        Objects.requireNonNull(solverEstimatedBytes);
        Objects.requireNonNull(topologyEstimatedBytes);
        Objects.requireNonNull(matrixPayloadBytes);
        Objects.requireNonNull(options);
        warnings = List.copyOf(warnings);
        if (vertexCount < 3 || undirectedEdges < 0 || solverEstimatedBytes.signum() < 0
                || topologyEstimatedBytes.signum() < 0 || matrixPayloadBytes.signum() < 0) {
            throw new IllegalArgumentException("Invalid preflight metadata.");
        }
    }

    public static ShortestPathPreflight analyze(RefugeGraph graph, ShortestPathExecutionOptions options) {
        Objects.requireNonNull(graph);
        return analyze(graph.topology(), options);
    }

    /** Allows checks on implicit complete topology before allocating a TspProblem matrix. */
    public static ShortestPathPreflight analyze(RefugeTopology topology, ShortestPathExecutionOptions options) {
        Objects.requireNonNull(topology);
        Objects.requireNonNull(options);
        int size = topology.size();
        BigInteger n = BigInteger.valueOf(size);
        BigInteger solver = n.multiply(BigInteger.valueOf(256)).add(BigInteger.valueOf(1_048_576));
        BigInteger matrix = n.multiply(n).multiply(BigInteger.valueOf(Long.BYTES));
        boolean allowed = size <= Integer.MAX_VALUE - 8
                && solver.compareTo(BigInteger.valueOf(options.maxEstimatedBytes())) <= 0;
        return new ShortestPathPreflight(size, topology.edgeCount(), solver, topology.estimatedStorageBytes(),
                matrix, options, allowed, List.of(
                "Solver estimate: 256*N + 1 MiB; budget excludes topology and existing matrix.",
                "Matrix payload: 8*N²; excludes object overhead and generation/defensive copies.",
                "Undirected connections can be examined in both directions; time/cancellation are cooperative."));
    }

    public void requireAllowed() {
        if (!executionAllowed) {
            throw new IllegalArgumentException("Shortest-path preflight refused: solver estimate="
                    + solverEstimatedBytes + ", budget=" + options.maxEstimatedBytes() + ", N=" + vertexCount);
        }
    }
}
