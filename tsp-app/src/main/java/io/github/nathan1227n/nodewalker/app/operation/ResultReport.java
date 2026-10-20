package io.github.nathan1227n.nodewalker.app.operation;

import java.util.OptionalLong;

/** Presentation copy only: does not certify, solve or change a mathematical result. */
public record ResultReport(String title, String context, String status, String conclusion,
                           String valueLabel, String value, String routeLabel, String route,
                           String elapsed, String metrics, String reason) {
    public static ResultReport of(OperationalSnapshot snapshot) {
        var hud = HudPresentation.of(snapshot);
        String context = snapshot.n() + " refúgios · seed " + snapshot.seed() + " · instância #" + snapshot.instanceId();
        if (snapshot.result() instanceof ExecutionRecord.Tsp run) {
            var result = run.result();
            return new ResultReport(run.request().algorithm() == ExecutionRequest.Algorithm.BRUTE_FORCE ? "Força Bruta" : "Backtracking",
                    context + " · execução #" + run.runId(), friendlyStatus(run.status()),
                    result.optimalityGuaranteed() ? "Ótimo certificado: todas as cidades são visitadas e o ciclo retorna à BASE 0."
                            : result.route().isPresent() ? "Resultado parcial: ciclo válido encontrado, sem prova de que seja o melhor."
                            : "A execução terminou sem encontrar um ciclo completo.",
                    result.optimalityGuaranteed() ? "Custo ótimo do ciclo" : "Custo do ciclo encontrado", hud.cost(),
                    "Ciclo · saída e retorno à BASE 0", hud.route(), HudPresentation.elapsed(result.metrics().elapsedNanos()), hud.metrics(),
                    result.reason().orElse(""));
        }
        if (snapshot.result() instanceof ExecutionRecord.Paths run) {
            var result = run.result(); int target = snapshot.destination();
            String conclusion = switch (result.vertexState(target)) {
                case CONFIRMADO -> "Distância mínima confirmada para este destino. Este caminho não é um ciclo TSP.";
                case INALCANCAVEL -> "Destino inalcançável: a busca concluída comprovou que não há caminho.";
                case NAO_RESOLVIDO -> "Destino ainda não resolvido: nenhuma distância tentativa é apresentada como mínima.";
            };
            return new ResultReport("Dijkstra clássico", context + " · execução #" + run.runId() + "\nOrigem " + result.origin()
                    + " → destino " + target + " · " + switch (result.graph().topology().kind()) {
                        case COMPLETE -> "Topologia completa";
                        case SPARSE_CONNECTED -> "Topologia esparsa conectada";
                        case MANUAL -> "Topologia manual";
                    }, friendlyStatus(run.status()), conclusion,
                    "Distância mínima confirmada", hud.cost(), "Caminho · origem → destino", hud.route(),
                    HudPresentation.elapsed(result.metrics().elapsedNanos()), hud.metrics(), result.reason().orElse(""));
        }
        return new ResultReport("Resultados", context, "Aguardando execução", "Execute um algoritmo na instância atual.",
                "Custo / distância", "não informado", "Sequência de refúgios", "Nenhum resultado selecionado.",
                HudPresentation.elapsed(OptionalLong.empty()), hud.metrics(), snapshot.diagnostic());
    }
    public static String friendlyStatus(String status) {
        return switch (status) {
            case "CONCLUIDO" -> "Concluído";
            case "CANCELADO" -> "Cancelado";
            case "LIMITE_ATINGIDO" -> "Limite atingido";
            case "ERRO" -> "Erro";
            default -> status;
        };
    }
    public String copyText() {
        return title + "\n" + context + "\nEstado: " + status + "\n" + conclusion + "\n" + valueLabel + ": " + value
                + "\n" + routeLabel + ": " + route + "\nTempo interno do solver: " + elapsed + "\n" + metrics
                + (reason.isEmpty() ? "" : "\nMotivo: " + reason);
    }
}
