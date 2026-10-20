package io.github.nathan1227n.nodewalker.app.operation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import static io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest.Algorithm.*;

/** Latest attempt per TSP algorithm, restricted to one instance. Dijkstra never enters this comparison. */
public record TspComparison(Optional<ExecutionRecord.Tsp> bruteForce, Optional<ExecutionRecord.Tsp> backtracking,
                             Verdict verdict, String headline, String explanation) {
    public enum Verdict { WAITING, PARTIAL, AGREEMENT, DIVERGENCE }
    public static TspComparison of(long instanceId, List<ExecutionRecord> history) {
        var tsp = history.stream().filter(e -> e.instanceId() == instanceId).filter(ExecutionRecord.Tsp.class::isInstance)
                .map(ExecutionRecord.Tsp.class::cast).toList();
        var brute = tsp.stream().filter(e -> e.request().algorithm() == BRUTE_FORCE).max(Comparator.comparingLong(ExecutionRecord::runId));
        var back = tsp.stream().filter(e -> e.request().algorithm() == BACKTRACKING).max(Comparator.comparingLong(ExecutionRecord::runId));
        if (brute.isEmpty() || back.isEmpty())
            return new TspComparison(brute, back, Verdict.WAITING, "Aguardando resultado de " + (brute.isEmpty() && back.isEmpty() ? "ambos os algoritmos" : brute.isEmpty() ? "Força Bruta" : "Backtracking"),
                    "Execute cada algoritmo na mesma instância. Os refúgios e a matriz serão preservados.");
        var a = brute.orElseThrow().result(); var b = back.orElseThrow().result();
        if (!a.optimalityGuaranteed() || !b.optimalityGuaranteed())
            return new TspComparison(brute, back, Verdict.PARTIAL, "Comparação parcial · ótimo ainda não comprovado por ambos",
                    "A tentativa mais recente de cada algoritmo é mostrada, inclusive quando cancelada ou limitada. Custos encontrados não certificam o ótimo.");
        boolean agree = a.cost().equals(b.cost()) && a.route().orElseThrow().indices().equals(b.route().orElseThrow().indices());
        return new TspComparison(brute, back, agree ? Verdict.AGREEMENT : Verdict.DIVERGENCE,
                agree ? "Mesmo custo ótimo e mesmo ciclo" : "Atenção: resultados certificados divergem",
                agree ? "As duas buscas concluíram e concordam no ótimo e no desempate canônico desta instância."
                        : "Os resultados concluídos divergem em custo ou ciclo canônico. Investigue antes de apresentar como concordância.");
    }
    public String copyText() {
        return headline + "\n" + explanation + "\n\n" + column("Força Bruta", bruteForce)
                + "\n\n" + column("Backtracking", backtracking)
                + "\n\nTempos internos desta execução; não constituem um benchmark. Contadores têm semânticas próprias de cada algoritmo.";
    }
    private static String column(String title, Optional<ExecutionRecord.Tsp> run) {
        if (run.isEmpty()) return title + ": ainda não executado";
        var r = run.orElseThrow(); var hud = HudPresentation.tsp(r);
        return title + " · execução #" + r.runId() + "\nEstado: " + ResultReport.friendlyStatus(r.status())
                + "\nCusto: " + hud.cost() + "\n" + hud.certification() + "\nCiclo: " + hud.route()
                + "\n" + hud.metrics() + "\nOrçamento: " + HudPresentation.optional(r.request().tspOptions().maxVisitedNodes())
                + " nós · " + HudPresentation.optional(r.request().tspOptions().maxElapsedMillis()) + " ms" + r.result().reason().map(reason -> "\nMotivo: " + reason).orElse("");
    }
}
