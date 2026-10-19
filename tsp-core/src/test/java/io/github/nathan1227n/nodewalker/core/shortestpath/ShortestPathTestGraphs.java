package io.github.nathan1227n.nodewalker.core.shortestpath;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import java.util.List;

final class ShortestPathTestGraphs {
    static RefugeGraph chain() {
        return manual(new long[][] {{0, 2, 100}, {2, 0, 3}, {100, 3, 0}},
                new RefugeTopology.Edge(0, 1), new RefugeTopology.Edge(1, 2));
    }

    static RefugeGraph manual(long[][] costs, RefugeTopology.Edge... edges) {
        return new RefugeGraph(ContractTestProblems.problem(costs), RefugeTopology.manual(costs.length, List.of(edges)));
    }
}
