package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.solver.*;

/** Injection boundary for deterministic concurrency tests; production delegates to audited core APIs. */
public interface OperationalServices {
    default TspProblem generate(int n, long seed, GenerationOptions options) { return TspGenerator.generate(n, seed, options); }
    default TspResult tsp(TspProblem problem, ExecutionRequest request, SolverCancellationToken token) {
        TspSolver solver = request.algorithm() == ExecutionRequest.Algorithm.BRUTE_FORCE ? new BruteForceSolver() : new BacktrackingSolver();
        return solver.solve(problem, request.tspOptions(), token);
    }
    default ShortestPathResult paths(RefugeGraph graph, ExecutionRequest request, ShortestPathCancellationToken token) {
        return new DijkstraShortestPathSolver().solve(graph, request.origin(), request.pathOptions(), token);
    }
}
