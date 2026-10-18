package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.solver.*;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ResultPresentationTest {
    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"CONCLUIDO", "CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void fourStatesHaveUnambiguousExactCertification(SolverStatus state) {
        var p = WorldContractsTest.zeroProblem();
        var route = new TspRoute(List.of(0, 1, 2, 0));
        var result = state == SolverStatus.CONCLUIDO ? TspResult.completed(p, route, 0, SolverMetrics.empty()) :
                TspResult.interrupted(p, state, Optional.of(route), OptionalLong.of(0), SolverMetrics.empty(), "reason: " + state);
        var presentation = ResultPresentation.of(Optional.of(result));
        assertEquals(state.name(), presentation.status());
        assertEquals(state == SolverStatus.CONCLUIDO, presentation.certified());
        assertEquals(state == SolverStatus.CONCLUIDO ? "ÓTIMO CERTIFICADO" : "ÓTIMO NÃO CERTIFICADO", presentation.certification());
        assertEquals(state == SolverStatus.CONCLUIDO ? "ROTA ÓTIMA" : "MELHOR ROTA CONHECIDA", presentation.headline());
        assertTrue(presentation.hasRoute());
        assertEquals(result.reason(), presentation.reason());
        if (!presentation.certified())
            assertEquals("MELHOR ROTA CONHECIDA — ÓTIMO NÃO CERTIFICADO", presentation.routeLabel());
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void noIncumbentDoesNotClaimAnyRoute(SolverStatus status) {
        var result = TspResult.interrupted(WorldContractsTest.zeroProblem(), status, Optional.empty(), OptionalLong.empty(), SolverMetrics.empty(), "reason");
        var p = ResultPresentation.of(Optional.of(result));
        assertEquals("SEM ROTA DISPONÍVEL", p.headline());
        assertEquals("ÓTIMO NÃO CERTIFICADO", p.certification());
        assertFalse(p.hasRoute());
        assertFalse(p.certified());
    }

    @Test
    void emptyAndTransitionCannotKeepPreviousCertification() {
        var p = WorldContractsTest.zeroProblem();
        var r = new TspRoute(List.of(0, 1, 2, 0));
        var model = TspSceneModel.of(p, Optional.of(TspResult.completed(p, r, 0, SolverMetrics.empty())));
        assertTrue(model.presentation().certified());
        var interrupted = model.withResult(Optional.of(TspResult.interrupted(p, SolverStatus.LIMITE_ATINGIDO, Optional.of(r), OptionalLong.of(0), SolverMetrics.empty(), "nodes")));
        assertFalse(interrupted.presentation().certified());
        assertEquals("ÓTIMO NÃO CERTIFICADO", interrupted.presentation().certification());
        var cleared = interrupted.withResult(Optional.empty());
        assertEquals("", cleared.presentation().certification());
        assertTrue(cleared.segments().isEmpty());
        assertSame(model.geometry(false), cleared.geometry(false));
    }
}
