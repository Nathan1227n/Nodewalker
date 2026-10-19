package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import io.github.nathan1227n.nodewalker.core.solver.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathExecutionOptions;
import java.util.Objects;

/** Frozen execution configuration. Destination is deliberately not a solver input/cache key. */
public record ExecutionRequest(Algorithm algorithm, int origin, RefugeTopology.Kind topology,
                               long topologySeed, int extraConnections, SolverExecutionOptions tspOptions,
                               ShortestPathExecutionOptions pathOptions) {
    public enum Algorithm {
        BRUTE_FORCE("TSP / Força Bruta"), BACKTRACKING("TSP / Backtracking"), DIJKSTRA("Caminhos mínimos / Dijkstra clássico");
        private final String label;
        Algorithm(String label) { this.label = label; }
        @Override public String toString() { return label; }
        public boolean tsp() { return this != DIJKSTRA; }
        public SolverAlgorithm coreAlgorithm() {
            if (!tsp()) throw new IllegalStateException("Dijkstra is not a TspSolver");
            return this == BRUTE_FORCE ? SolverAlgorithm.BRUTE_FORCE : SolverAlgorithm.BACKTRACKING;
        }
    }
    public ExecutionRequest {
        Objects.requireNonNull(algorithm); Objects.requireNonNull(topology);
        if (algorithm.tsp()) origin = 0;
        Objects.requireNonNull(tspOptions); Objects.requireNonNull(pathOptions);
        if (origin < 0 || extraConnections < 0) throw new IllegalArgumentException("Origem/extras inválidos");
        if (topology == RefugeTopology.Kind.MANUAL) throw new IllegalArgumentException("Editor manual não disponível no painel");
        if (!tspOptions.hasLimits()) throw new IllegalArgumentException("O painel exige limites TSP explícitos");
    }
    public static ExecutionRequest defaults() {
        return new ExecutionRequest(Algorithm.BACKTRACKING, 0, RefugeTopology.Kind.SPARSE_CONNECTED, 23, 0,
                SolverExecutionOptions.limits(1_000_000, 2000), ShortestPathExecutionOptions.demonstration());
    }
    public void validateFor(int n) {
        if (!algorithm.tsp() && origin >= n) throw new IllegalArgumentException("Origem fora da instância");
        if (!algorithm.tsp() && extraConnections > n - 2) throw new IllegalArgumentException("Conexões extras devem estar entre 0 e N−2");
    }
}
