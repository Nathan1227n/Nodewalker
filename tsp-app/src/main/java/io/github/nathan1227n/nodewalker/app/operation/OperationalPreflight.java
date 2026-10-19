package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.app.world.VisualBudget;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathPreflight;
import io.github.nathan1227n.nodewalker.core.solver.SolverPreflight;

public final class OperationalPreflight {
    private OperationalPreflight() { }
    public static String tsp(int n, ExecutionRequest request) {
        var report = SolverPreflight.analyze(request.algorithm().coreAlgorithm(), n, request.tspOptions());
        report.requireAllowed();
        return "N=" + n + " · origem 0 · " + request.algorithm() + "\n(N−1)! "
                + (report.exactSearchSpace() ? "= " : ">= ") + report.theoreticalRoutes()
                + "\nAté " + HudPresentation.optional(request.tspOptions().maxVisitedNodes()) + " nós / "
                + HudPresentation.optional(request.tspOptions().maxElapsedMillis()) + " ms"
                + "\nFaixa recomendada: N≤" + request.tspOptions().policy().recommendedMaxCities(request.algorithm().coreAlgorithm())
                + (report.outsideRecommendedRange() ? " · FORA DA FAIXA: confirme execução limitada" : "")
                + "\nCusto fatorial; limites não garantem conclusão.";
    }
    public static String paths(RefugeGraph graph, ExecutionRequest request) {
        var p = ShortestPathPreflight.analyze(graph, request.pathOptions()); p.requireAllowed();
        return "Dijkstra clássico · origem " + request.origin() + "\nV=" + p.vertexCount() + " · " + io.github.nathan1227n.nodewalker.app.world.ShortestPathSceneModel.topologyLabel(graph.topology().kind())
                + " · E=" + p.undirectedEdges() + " conexões\nAté " + request.pathOptions().maxProcessedVertices()
                + " vértices / " + request.pathOptions().maxExaminedEdges() + " entradas / " + request.pathOptions().maxElapsedMillis()
                + " ms\nEstimativas em bytes: solver " + p.solverEstimatedBytes() + "; topologia " + p.topologyEstimatedBytes()
                + "; matriz (payload) " + p.matrixPayloadBytes() + ". Não são reservas de heap.";
    }
    public static String generation(int n) {
        VisualBudget.requireAllowed(n);
        return "Geração: " + TspGenerator.estimatePeakMemoryBytes(n) + " bytes estimados; visual: "
                + VisualBudget.estimatedBytes(n) + " bytes / " + VisualBudget.estimatedTriangles(n) + " triângulos estimados.";
    }
}
