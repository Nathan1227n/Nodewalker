package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class TspResultTest {
    private static final TspRoute ROUTE = new TspRoute(List.of(0, 1, 2, 0));
    private static final SolverMetrics METRICS = SolverMetrics.empty();

    @Test
    void constructsCompletedResultWithValidatedRouteCostAndNoInterruptionReason() {
        TspResult result = TspResult.completed(ContractTestProblems.triangle(), ROUTE, 45L, METRICS);

        assertEquals(SolverStatus.CONCLUIDO, result.status());
        assertEquals(ROUTE, result.route().orElseThrow());
        assertEquals(45L, result.cost().orElseThrow());
        assertEquals(METRICS, result.metrics());
        assertTrue(result.reason().isEmpty());
        assertTrue(result.optimalityGuaranteed());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE, 0, 44, 46, Long.MAX_VALUE})
    void rejectsNegativeOrInconsistentCompletedCost(long declaredCost) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TspResult.completed(ContractTestProblems.triangle(), ROUTE, declaredCost, METRICS));

        assertTrue(error.getMessage().contains("custo declarado"));
    }

    @Test
    void rejectsCompletedCycleWithIncompatibleCityCount() {
        assertThrows(IllegalArgumentException.class, () -> TspResult.completed(ContractTestProblems.triangle(),
                new TspRoute(List.of(0, 1, 2, 3, 0)), 45L, METRICS));
    }

    @Test
    void rejectsCompletedReverseOrientationAndRequiresExplicitCanonicalization() {
        TspProblem problem = ContractTestProblems.triangle();
        TspRoute reverse = new TspRoute(List.of(0, 2, 1, 0));
        assertEquals(45L, RouteValidator.validateAndCost(problem, reverse));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TspResult.completed(problem, reverse, 45L, METRICS));

        assertTrue(error.getMessage().contains("canônica"));
        assertEquals(List.of(0, 2, 1, 0), reverse.indices());
        assertEquals(ROUTE, TspResult.completed(problem, reverse.canonical(), 45L, METRICS).route().orElseThrow());
    }

    @Test
    void acceptsRepresentableLongMaximumWithoutUsingAnInfinitySentinel() {
        TspResult result = TspResult.completed(ContractTestProblems.triangle(Long.MAX_VALUE, 0, 0),
                ROUTE, Long.MAX_VALUE, METRICS);

        assertEquals(Long.MAX_VALUE, result.cost().orElseThrow());
        assertTrue(result.route().isPresent());
        assertTrue(result.optimalityGuaranteed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overflowCannotBecomeACompletedResult(boolean onReturn) {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, onReturn ? 0 : 1, onReturn ? 1 : 0);

        assertThrows(ArithmeticException.class, () -> TspResult.completed(problem, ROUTE, Long.MAX_VALUE, METRICS));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsNullCompletedArguments(int field) {
        assertThrows(NullPointerException.class, () -> TspResult.completed(
                field == 0 ? null : ContractTestProblems.triangle(), field == 1 ? null : ROUTE,
                45L, field == 2 ? null : METRICS));
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void representsInterruptionWithoutIncumbentOrFalseOptimality(SolverStatus status) {
        TspResult result = TspResult.interrupted(ContractTestProblems.triangle(), status,
                Optional.empty(), OptionalLong.empty(), METRICS, "Diagnóstico da execução: " + status);

        assertEquals(status, result.status());
        assertTrue(result.route().isEmpty());
        assertTrue(result.cost().isEmpty());
        assertEquals(METRICS, result.metrics());
        assertEquals("Diagnóstico da execução: " + status, result.reason().orElseThrow());
        assertFalse(result.optimalityGuaranteed());
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void preservesValidReverseIncumbentAndReportedPartialMetrics(SolverStatus status) {
        TspRoute reverse = new TspRoute(List.of(0, 2, 1, 0));
        // Snapshot manual de teste; nenhuma instrumentação de busca é implementada.
        SolverMetrics partial = new SolverMetrics(OptionalLong.of(17), OptionalLong.of(1),
                OptionalLong.empty(), Optional.empty());
        TspResult result = TspResult.interrupted(ContractTestProblems.triangle(), status,
                Optional.of(reverse), OptionalLong.of(45), partial, "Execução interrompida");

        assertEquals(reverse, result.route().orElseThrow());
        assertEquals(45L, result.cost().orElseThrow());
        assertEquals(partial, result.metrics());
        assertFalse(result.route().orElseThrow().isCanonical());
        assertFalse(result.optimalityGuaranteed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsRouteOrCostPresentWithoutItsPair(boolean onlyRoute) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TspResult.interrupted(ContractTestProblems.triangle(), SolverStatus.CANCELADO,
                        onlyRoute ? Optional.of(ROUTE) : Optional.empty(),
                        onlyRoute ? OptionalLong.empty() : OptionalLong.of(45), METRICS, "Cancelado"));

        assertTrue(error.getMessage().contains("ambos presentes ou ambos ausentes"));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE, 44, 46, Long.MAX_VALUE})
    void rejectsInvalidIncumbentCostInAnInterruptedResult(long declaredCost) {
        assertThrows(IllegalArgumentException.class, () -> TspResult.interrupted(ContractTestProblems.triangle(),
                SolverStatus.LIMITE_ATINGIDO, Optional.of(ROUTE), OptionalLong.of(declaredCost), METRICS, "Limite"));
    }

    @Test
    void rejectsInterruptedIncumbentFromAnotherProblemSize() {
        assertThrows(IllegalArgumentException.class, () -> TspResult.interrupted(ContractTestProblems.fourCityReference(),
                SolverStatus.ERRO, Optional.of(ROUTE), OptionalLong.of(45), METRICS, "Falha"));
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void rejectsOverflowingIncumbentEvenInAnInterruptedResult(SolverStatus status) {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, 0, 1);

        assertThrows(ArithmeticException.class, () -> TspResult.interrupted(problem, status,
                Optional.of(ROUTE), OptionalLong.of(Long.MAX_VALUE), METRICS, "Interrompido"));
    }

    @Test
    void canReportOverflowAsErrorWithoutStoringTheInvalidIncumbent() {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, 0, 1);
        ArithmeticException failure = assertThrows(ArithmeticException.class,
                () -> RouteValidator.validateAndCost(problem, ROUTE));

        TspResult result = TspResult.interrupted(problem, SolverStatus.ERRO, Optional.empty(),
                OptionalLong.empty(), METRICS, failure.getMessage());

        assertEquals(SolverStatus.ERRO, result.status());
        assertTrue(result.reason().orElseThrow().contains("Long.MAX_VALUE"));
        assertTrue(result.route().isEmpty());
        assertTrue(result.cost().isEmpty());
        assertFalse(result.optimalityGuaranteed());
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"PRONTO", "EXECUTANDO", "CANCELANDO", "CONCLUIDO"})
    void rejectsStatesThatCannotDescribeInterruption(SolverStatus status) {
        assertThrows(IllegalArgumentException.class, () -> TspResult.interrupted(ContractTestProblems.triangle(),
                status, Optional.empty(), OptionalLong.empty(), METRICS, "Interrompido"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void requiresANonBlankInterruptionReason(String reason) {
        assertThrows(IllegalArgumentException.class, () -> TspResult.interrupted(ContractTestProblems.triangle(),
                SolverStatus.ERRO, Optional.empty(), OptionalLong.empty(), METRICS, reason));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rejectsNullInterruptedArguments(int field) {
        assertThrows(NullPointerException.class, () -> TspResult.interrupted(
                field == 0 ? null : ContractTestProblems.triangle(), field == 1 ? null : SolverStatus.CANCELADO,
                field == 2 ? null : Optional.empty(), field == 3 ? null : OptionalLong.empty(),
                field == 4 ? null : METRICS, field == 5 ? null : "Cancelado"));
    }

    @Test
    void zeroCostIsPresentAndDiffersFromHavingNoIncumbent() {
        TspProblem problem = ContractTestProblems.triangle(0, 0, 0);
        TspResult completed = TspResult.completed(problem, ROUTE, 0L, METRICS);
        TspResult partial = TspResult.interrupted(problem, SolverStatus.CANCELADO,
                Optional.of(ROUTE), OptionalLong.of(0), METRICS, "Cancelado");

        assertEquals(0L, completed.cost().orElseThrow());
        assertEquals(0L, partial.cost().orElseThrow());
        assertTrue(partial.route().isPresent());
        assertFalse(partial.optimalityGuaranteed());
    }

    @Test
    void equalCostResultsPreserveTheirDistinctCanonicalCyclesWithoutChoosingATieWinner() {
        TspProblem problem = ContractTestProblems.equalEdges();
        TspRoute first = new TspRoute(List.of(0, 1, 2, 3, 0));
        TspRoute other = new TspRoute(List.of(0, 1, 3, 2, 0));
        TspResult firstResult = TspResult.completed(problem, first, 28L, METRICS);
        TspResult otherResult = TspResult.completed(problem, other, 28L, METRICS);

        assertEquals(firstResult.cost(), otherResult.cost());
        assertEquals(first, firstResult.route().orElseThrow());
        assertEquals(other, otherResult.route().orElseThrow());
    }

    @Test
    void acceptsKnownFourCityReferenceAndPreservesSnapshotImmutability() {
        List<Integer> input = new ArrayList<>(List.of(0, 1, 2, 3, 0));
        TspResult result = TspResult.completed(ContractTestProblems.fourCityReference(), new TspRoute(input), 300L, METRICS);
        input.clear();

        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        assertEquals(300L, result.cost().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> result.route().orElseThrow().indices().clear());
    }
}
