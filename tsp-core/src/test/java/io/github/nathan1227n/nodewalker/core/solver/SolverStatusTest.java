package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SolverStatusTest {
    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"CONCLUIDO", "CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void identifiesFinalExecutionStates(SolverStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"PRONTO", "EXECUTANDO", "CANCELANDO"})
    void keepsPreparationExecutionAndCancellationRequestNonTerminal(SolverStatus status) {
        assertFalse(status.isTerminal());
    }
}
