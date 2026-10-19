package io.github.nathan1227n.nodewalker.core.graph;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.HashSet;
import java.util.Optional;

/** Immutable availability of undirected connections, without weights or terrain costs. */
public final class RefugeTopology {
    public static final long DEFAULT_MAX_ESTIMATED_BYTES = 64L * 1024 * 1024;
    public enum Kind { COMPLETE, SPARSE_CONNECTED, MANUAL }

    /** Normalized undirected edge. A missing edge is represented by topology, never by a cost. */
    public record Edge(int from, int to) {
        public Edge {
            if (from < 0 || to < 0 || from == to) {
                throw new IllegalArgumentException("Connection requires distinct nonnegative indices.");
            }
            if (from > to) {
                int swap = from;
                from = to;
                to = swap;
            }
        }
    }

    private final int size;
    private final long edgeCount;
    private final Kind kind;
    private final int[][] neighbors; // null denotes an implicit complete topology, not an absent distance
    private final OptionalLong seed;

    private RefugeTopology(int size, long edgeCount, Kind kind, int[][] neighbors, OptionalLong seed) {
        this.size = size;
        this.edgeCount = edgeCount;
        this.kind = kind;
        this.neighbors = neighbors;
        this.seed = seed;
    }

    public static RefugeTopology complete(int size) {
        validateSize(size);
        return new RefugeTopology(size, (long) size * (size - 1) / 2, Kind.COMPLETE, null, OptionalLong.empty());
    }

    public static RefugeTopology manual(int size, List<Edge> edges) {
        return manual(size, edges, DEFAULT_MAX_ESTIMATED_BYTES);
    }

    public static RefugeTopology manual(int size, List<Edge> edges, long maxEstimatedBytes) {
        return build(size, edges, Kind.MANUAL, OptionalLong.empty(), maxEstimatedBytes, () -> { });
    }

    static RefugeTopology build(int size, List<Edge> edges, Kind kind, OptionalLong seed,
                                long maxBytes, Runnable checkpoint) {
        Objects.requireNonNull(edges);
        requireStorage(size, edges.size(), maxBytes);
        checkpoint.run();
        var snapshot = List.copyOf(edges);
        var keys = new HashSet<Long>();
        int[] degrees = new int[size];
        for (Edge edge : snapshot) {
            checkpoint.run();
            Objects.checkIndex(edge.to(), size);
            long key = ((long) edge.from() << 32) | edge.to();
            if (!keys.add(key)) throw new IllegalArgumentException("Duplicate connection: " + edge);
            degrees[edge.from()]++;
            degrees[edge.to()]++;
        }
        int[][] adjacency = new int[size][];
        for (int v = 0; v < size; v++) {
            checkpoint.run();
            adjacency[v] = new int[degrees[v]];
        }
        Arrays.fill(degrees, 0);
        for (Edge edge : snapshot) {
            checkpoint.run();
            adjacency[edge.from()][degrees[edge.from()]++] = edge.to();
            adjacency[edge.to()][degrees[edge.to()]++] = edge.from();
        }
        for (int[] row : adjacency) {
            checkpoint.run();
            Arrays.sort(row);
        }
        checkpoint.run();
        return new RefugeTopology(size, snapshot.size(), kind, adjacency, seed);
    }

    /** Conservative construction/storage model, not measured heap or a reservation. */
    public static BigInteger estimateExplicitStorageBytes(int size, long edges) {
        validateSize(size);
        if (edges < 0 || edges > (long) size * (size - 1) / 2) {
            throw new IllegalArgumentException("Invalid number of undirected connections.");
        }
        return BigInteger.valueOf(size).multiply(BigInteger.valueOf(256))
                .add(BigInteger.valueOf(edges).multiply(BigInteger.valueOf(128)))
                .add(BigInteger.valueOf(4096));
    }

    static void requireStorage(int size, long edges, long maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("Memory estimate budget must be positive.");
        if (size > Integer.MAX_VALUE - 8 || edges > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Explicit topology exceeds supported array indexing.");
        }
        if (estimateExplicitStorageBytes(size, edges).compareTo(BigInteger.valueOf(maxBytes)) > 0) {
            throw new IllegalArgumentException("Topology memory estimate exceeds budget: " + maxBytes);
        }
    }

    private static void validateSize(int size) {
        if (size < 3) throw new IllegalArgumentException("Refuge topology requires N >= 3.");
    }

    public int size() { return size; }
    public long edgeCount() { return edgeCount; }
    public Kind kind() { return kind; }
    public OptionalLong seed() { return seed; }
    public Optional<String> generationVersion() {
        return kind == Kind.SPARSE_CONNECTED ? Optional.of(RefugeTopologyGenerator.VERSION) : Optional.empty();
    }
    public BigInteger estimatedStorageBytes() {
        return kind == Kind.COMPLETE ? BigInteger.valueOf(1024) : estimateExplicitStorageBytes(size, edgeCount);
    }

    public int degree(int vertex) {
        Objects.checkIndex(vertex, size);
        return neighbors == null ? size - 1 : neighbors[vertex].length;
    }

    /** Ascending neighbors; no adjacency copy or per-query list allocation. */
    public int neighborAt(int vertex, int position) {
        Objects.checkIndex(position, degree(vertex));
        return neighbors == null ? (position < vertex ? position : position + 1) : neighbors[vertex][position];
    }

    public boolean hasEdge(int from, int to) {
        Objects.checkIndex(from, size);
        Objects.checkIndex(to, size);
        return from != to && (neighbors == null || Arrays.binarySearch(neighbors[from], to) >= 0);
    }
}
