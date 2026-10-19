package io.github.nathan1227n.nodewalker.core.shortestpath;

import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable confirmed shortest paths; never a Hamiltonian cycle or a TspResult. */
public final class ShortestPathResult {
    public enum VertexState { CONFIRMADO, INALCANCAVEL, NAO_RESOLVIDO }

    private final RefugeGraph graph;
    private final int origin;
    private final ShortestPathStatus status;
    private final BigInteger[] distances; // null entry = no confirmed distance, including on interruption
    private final int[] predecessors;
    private final ShortestPathMetrics metrics;
    private final Optional<String> reason;

    private ShortestPathResult(RefugeGraph graph, int origin, ShortestPathStatus status,
                               BigInteger[] distances, int[] predecessors,
                               ShortestPathMetrics metrics, Optional<String> reason) {
        this.graph = graph;
        this.origin = origin;
        this.status = status;
        this.distances = distances;
        this.predecessors = predecessors;
        this.metrics = metrics;
        this.reason = reason;
    }

    /** Package-only validated factory; rank strictly decreases along predecessors, even on zero edges. */
    static ShortestPathResult create(RefugeGraph graph, int origin, ShortestPathStatus status,
                                      BigInteger[] labels, int[] parents, int[] rank,
                                      ShortestPathMetrics metrics, String reason) {
        Objects.requireNonNull(graph);
        Objects.checkIndex(origin, graph.size());
        Objects.requireNonNull(status);
        Objects.requireNonNull(metrics);
        if ((status == ShortestPathStatus.CONCLUIDO) != (reason == null)) {
            throw new IllegalArgumentException("Only interrupted/error results require a reason.");
        }
        if (reason != null && reason.isBlank()) throw new IllegalArgumentException("Empty reason.");
        BigInteger[] confirmed = null;
        int[] predecessors = null;
        if (labels != null) {
            if (labels.length != graph.size() || parents.length != graph.size() || rank.length != graph.size()) {
                throw new IllegalArgumentException("Result arrays mismatch graph.");
            }
            confirmed = new BigInteger[labels.length];
            predecessors = parents.clone();
            for (int v = 0; v < labels.length; v++) {
                if (rank[v] == 0) {
                    predecessors[v] = -1;
                    continue;
                }
                BigInteger distance = Objects.requireNonNull(labels[v]);
                if (distance.signum() < 0) throw new IllegalStateException("Negative distance.");
                if (v == origin) {
                    if (distance.signum() != 0 || parents[v] != -1 || rank[v] != 1) {
                        throw new IllegalStateException("Invalid origin label.");
                    }
                } else {
                    int p = Objects.checkIndex(parents[v], graph.size());
                    if (rank[p] <= 0 || rank[p] >= rank[v] || !graph.topology().hasEdge(p, v)
                            || !labels[p].add(BigInteger.valueOf(graph.cost(p, v))).equals(distance)) {
                        throw new IllegalStateException("Invalid predecessor/distance.");
                    }
                }
                confirmed[v] = distance;
            }
        }
        if (status == ShortestPathStatus.CONCLUIDO && (confirmed == null || confirmed[origin] == null)) {
            throw new IllegalStateException("Completed run requires confirmed origin.");
        }
        return new ShortestPathResult(graph, origin, status, confirmed, predecessors, metrics, Optional.ofNullable(reason));
    }

    public RefugeGraph graph() { return graph; }
    public int origin() { return origin; }
    public ShortestPathStatus status() { return status; }
    public ShortestPathMetrics metrics() { return metrics; }
    public Optional<String> reason() { return reason; }
    public boolean allDistancesResolved() { return status == ShortestPathStatus.CONCLUIDO; }

    public VertexState vertexState(int vertex) {
        if (distanceTo(vertex).isPresent()) return VertexState.CONFIRMADO;
        return allDistancesResolved() ? VertexState.INALCANCAVEL : VertexState.NAO_RESOLVIDO;
    }

    /** Only fixed distances are public; tentative labels are discarded from interrupted snapshots. */
    public Optional<BigInteger> distanceTo(int vertex) {
        Objects.checkIndex(vertex, graph.size());
        return distances == null ? Optional.empty() : Optional.ofNullable(distances[vertex]);
    }

    public OptionalInt predecessorOf(int vertex) {
        if (distanceTo(vertex).isEmpty() || vertex == origin) return OptionalInt.empty();
        return OptionalInt.of(predecessors[vertex]);
    }

    public Optional<List<Integer>> pathTo(int destination) {
        if (distanceTo(destination).isEmpty()) return Optional.empty();
        var reversed = new ArrayList<Integer>();
        int current = destination;
        for (int steps = 0; steps < graph.size(); steps++) {
            reversed.add(current);
            if (current == origin) return Optional.of(List.copyOf(reversed.reversed()));
            current = predecessors[current];
        }
        throw new IllegalStateException("Cyclic predecessor chain.");
    }
}
