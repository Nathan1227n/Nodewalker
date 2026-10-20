package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.app.world.ResultPresentation;
import io.github.nathan1227n.nodewalker.core.solver.SolverMetrics;
import java.util.*;

/** Plain text, headless-testable. Optional counters never become invented zeroes. */
public record HudPresentation(String status, String headline, String certification, String cost,
                               String route, String metrics, String reason) {
    public static String optional(OptionalLong value) { return value.isPresent() ? Long.toString(value.getAsLong()) : "não informado"; }
    public static String elapsed(OptionalLong value) {
        return value.isPresent() ? String.format(Locale.ROOT, "%.3f ms", value.getAsLong() / 1e6) : "não informado";
    }
    public static String sequence(List<Integer> path) { return String.join(" → ", path.stream().map(Object::toString).toList()); }
    public static HudPresentation of(OperationalSnapshot snapshot) {
        if (snapshot.result() instanceof ExecutionRecord.Tsp run) return tsp(run);
        if (snapshot.result() instanceof ExecutionRecord.Paths run) {
            var r = run.result(); int target = snapshot.destination(); var m = r.metrics();
            return new HudPresentation(r.status().name(), "CAMINHO MÍNIMO · destino " + target + " / " + r.vertexState(target),
                    r.distanceTo(target).isPresent() ? "DISTÂNCIA CONFIRMADA" : "SEM DISTÂNCIA CONFIRMADA",
                    r.distanceTo(target).map(Object::toString).orElse("não informado"),
                    r.pathTo(target).map(HudPresentation::sequence).orElse("Nenhum caminho confirmado para o destino."),
                    "Origem: " + r.origin() + " · destino: " + target + "\nVértices processados: " + m.processedVertices()
                            + "\nEntradas examinadas: " + m.examinedEdges() + "\nTentativas de relaxamento: " + m.relaxationAttempts()
                            + "\nRelaxamentos bem-sucedidos: " + m.successfulRelaxations() + "\nPico da fila: " + m.peakQueueSize()
                            + "\nTempo interno: " + elapsed(m.elapsedNanos()), r.reason().orElse("Busca concluída; caminhos mínimos não são ciclos TSP."));
        }
        return new HudPresentation("SEM RESULTADO DE SOLVER", snapshot.request().algorithm().tsp() ? "CICLO HAMILTONIANO" : "CAMINHO MÍNIMO",
                "SEM RESULTADO", "não informado", "Execute sobre a instância atual.", "Métricas: não informado", snapshot.diagnostic());
    }
    public static HudPresentation tsp(ExecutionRecord.Tsp run) {
        var r = run.result(); var p = ResultPresentation.of(Optional.of(r)); var m = r.metrics();
        return new HudPresentation(r.status().name(), p.headline(), p.certification(), optional(r.cost()),
                r.route().map(route -> sequence(route.indices())).orElse("Sem incumbente: nenhuma rota desenhada."),
                tspMetrics(m), r.reason().orElse("Custo da matriz original; terreno apenas visual."));
    }
    public static String tspMetrics(SolverMetrics m) {
        return "Nós visitados: " + optional(m.visitedNodes()) + "\nRotas completas avaliadas: " + optional(m.completeRoutesEvaluated())
                + "\nFolhas descartadas: " + m.discardedRoutes().map(Object::toString).orElse("não informado")
                + "\nTempo interno: " + elapsed(m.elapsedNanos());
    }
    public static String history(List<ExecutionRecord> entries) {
        var text = new StringBuilder();
        for (var run : entries) {
            text.append('#').append(run.runId()).append(' ').append(run.request().algorithm()).append(" · ").append(run.status()).append('\n');
            if (run instanceof ExecutionRecord.Tsp r) {
                text.append("Custo: ").append(optional(r.result().cost())).append(" · tempo interno: ").append(elapsed(r.result().metrics().elapsedNanos()))
                        .append("\n").append(r.result().route().map(route -> sequence(route.indices())).orElse("Sem incumbente"))
                        .append("\n").append(tspMetrics(r.result().metrics())).append('\n');
            } else if (run instanceof ExecutionRecord.Paths r) text.append("Origem ").append(r.result().origin())
                    .append(" · ").append(r.result().graph().topology().kind()).append(" · tempo interno: ").append(elapsed(r.result().metrics().elapsedNanos())).append('\n');
        }
        var instances = entries.stream().map(ExecutionRecord::instanceId).distinct().toList();
        if (instances.size() == 1) {
            var comparison = TspComparison.of(instances.getFirst(), entries);
            if (comparison.bruteForce().isPresent() || comparison.backtracking().isPresent())
                text.append("COMPARAÇÃO TSP: ").append(comparison.headline()).append('.');
        }
        return text.isEmpty() ? "Nenhuma execução nesta instância." : text.toString();
    }
}
