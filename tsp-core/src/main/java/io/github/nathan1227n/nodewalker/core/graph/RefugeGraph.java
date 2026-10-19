package io.github.nathan1227n.nodewalker.core.graph;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.Objects;

/** Immutable view: topology controls availability; the original matrix supplies every edge weight. */
public record RefugeGraph(TspProblem problem, RefugeTopology topology) {
    public RefugeGraph {
        Objects.requireNonNull(problem);
        Objects.requireNonNull(topology);
        if (problem.size() != topology.size()) throw new IllegalArgumentException("Topology/problem size mismatch.");
    }

    public int size() { return problem.size(); }

    public long cost(int from, int to) {
        if (!topology.hasEdge(from, to)) throw new IllegalArgumentException("Connection unavailable.");
        return problem.cost(from, to);
    }
}
