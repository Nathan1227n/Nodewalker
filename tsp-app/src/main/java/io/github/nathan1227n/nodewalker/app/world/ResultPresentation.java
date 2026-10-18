package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.solver.SolverStatus;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;

import java.util.Optional;

/**
 * One immutable interpretation shared by text and renderers. No proof is inferred from a route/cost.
 */
public record ResultPresentation(String status, String headline, String certification,
                                 boolean certified, boolean hasRoute, Optional<String> reason) {
    public static ResultPresentation of(Optional<TspResult> result) {
        if (result.isEmpty())
            return new ResultPresentation("SEM RESULTADO", "SEM ROTA DISPONÍVEL", "", false, false, Optional.empty());
        TspResult r = result.orElseThrow();
        boolean proven = r.status() == SolverStatus.CONCLUIDO && r.optimalityGuaranteed();
        boolean route = r.route().isPresent();
        return new ResultPresentation(r.status().name(), proven ? "ROTA ÓTIMA" : route ? "MELHOR ROTA CONHECIDA" : "SEM ROTA DISPONÍVEL",
                proven ? "ÓTIMO CERTIFICADO" : "ÓTIMO NÃO CERTIFICADO", proven, route, r.reason());
    }

    public String routeLabel() {
        return certification.isEmpty() ? headline : headline + " — " + certification;
    }
}
