package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.app.world.PreparedWorld;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.solver.*;

/** Injection boundary for deterministic concurrency tests; production delegates to audited core APIs. */
public interface OperationalServices {
    default TspProblem generate(int n, long seed, GenerationOptions options) { return TspGenerator.generate(n, seed, options); }
    default PreparedWorld prepareWorld(TspProblem problem, Runnable checkpoint) { return PreparedWorld.prepare(problem, checkpoint); }
    default RefugeGraph prepareGraph(TspProblem problem, ExecutionRequest request, GenerationCancellationToken token) {
        var topology = request.topology() == RefugeTopology.Kind.COMPLETE ? RefugeTopology.complete(problem.size())
                : RefugeTopologyGenerator.connectedSparse(problem.size(), request.topologySeed(), request.extraConnections(),
                RefugeTopology.DEFAULT_MAX_ESTIMATED_BYTES, token);
        return new RefugeGraph(problem, topology);
    }
    default TspResult tsp(TspProblem problem, ExecutionRequest request, SolverCancellationToken token) {
        TspSolver solver = request.algorithm() == ExecutionRequest.Algorithm.BRUTE_FORCE ? new BruteForceSolver() : new BacktrackingSolver();
        return solver.solve(problem, request.tspOptions(), token);
    }
    default ShortestPathResult paths(RefugeGraph graph, ExecutionRequest request, ShortestPathCancellationToken token) {
        return new DijkstraShortestPathSolver().solve(graph, request.origin(), request.pathOptions(), token);
    }
}
