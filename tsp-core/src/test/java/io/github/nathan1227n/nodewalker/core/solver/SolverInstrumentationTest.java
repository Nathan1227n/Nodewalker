package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SolverInstrumentationTest {
    @ParameterizedTest
    @MethodSource("durations")
    void measuresOnlyTwoTicksAndSupportsZeroNegativeOriginsAndClockWrap(boolean backtracking,
                                                                       long start, long end, long duration) {
        AtomicInteger calls = new AtomicInteger();
        TspSolver solver = solver(backtracking, () -> switch (calls.getAndIncrement()) {
            case 0 -> start;
            case 1 -> end;
            default -> throw new AssertionError("Leitura extra do relógio");
        });

        TspResult result = solver.solve(ContractTestProblems.triangle());

        assertEquals(SolverStatus.CONCLUIDO, result.status());
        assertEquals(45, result.cost().orElseThrow());
        assertEquals(duration, result.metrics().elapsedNanos().orElseThrow());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsClockFailureBeforeSearchWithoutInventingDuration(boolean backtracking) {
        AtomicInteger calls = new AtomicInteger();
        TspSolver solver = solver(backtracking, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Relógio inicial indisponível");
        });

        TspResult result = solver.solve(ContractTestProblems.triangle());

        assertError(result);
        assertTrue(result.reason().orElseThrow().contains("Relógio inicial indisponível"));
        assertTrue(result.route().isEmpty());
        assertTrue(result.metrics().elapsedNanos().isEmpty());
        assertEquals(0, result.metrics().visitedNodes().orElseThrow());
        assertEquals(0, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(BigInteger.ZERO, result.metrics().discardedRoutes().orElseThrow());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clockFailureAfterSearchPreventsSuccessButPreservesValidIncumbentAndCounters(boolean backtracking) {
        AtomicInteger calls = new AtomicInteger();
        TspSolver solver = solver(backtracking, () -> {
            if (calls.getAndIncrement() == 0) {
                return 0;
            }
            throw new IllegalStateException("Relógio final indisponível");
        });

        TspResult result = solver.solve(ContractTestProblems.triangle());

        assertError(result);
        assertTrue(result.reason().orElseThrow().contains("Relógio final indisponível"));
        assertEquals(45, result.cost().orElseThrow());
        assertEquals(List.of(0, 1, 2, 0), result.route().orElseThrow().indices());
        assertEquals(2, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(backtracking ? 5 : 2, result.metrics().visitedNodes().orElseThrow());
        assertEquals(BigInteger.ZERO, result.metrics().discardedRoutes().orElseThrow());
        assertTrue(result.metrics().elapsedNanos().isEmpty());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void negativeElapsedDifferenceIsAnErrorWithoutClampingToZero(boolean backtracking) {
        AtomicInteger calls = new AtomicInteger();
        TspResult result = solver(backtracking, () -> calls.getAndIncrement() == 0 ? 10 : 9)
                .solve(ContractTestProblems.triangle());

        assertError(result);
        assertTrue(result.reason().orElseThrow().contains("Duração"));
        assertTrue(result.metrics().elapsedNanos().isEmpty());
        assertEquals(45, result.cost().orElseThrow());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondaryClockFailureDoesNotMaskTheOriginalCostOverflow(boolean backtracking) {
        AtomicInteger calls = new AtomicInteger();
        TspSolver solver = solver(backtracking, () -> {
            if (calls.getAndIncrement() == 0) {
                return 0;
            }
            throw new IllegalStateException("Falha secundária de tempo");
        });
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, 1, 0);

        TspResult result = solver.solve(problem);

        assertError(result);
        String reason = result.reason().orElseThrow();
        assertTrue(reason.contains("ArithmeticException"));
        assertTrue(reason.contains("Long.MAX_VALUE"));
        assertTrue(reason.contains("Falha secundária de tempo"));
        assertTrue(result.metrics().elapsedNanos().isEmpty());
        assertEquals(0, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(backtracking ? 2 : 1, result.metrics().visitedNodes().orElseThrow());
        assertEquals(BigInteger.ZERO, result.metrics().discardedRoutes().orElseThrow());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullInputIsRejectedBeforeReadingClockAndJvmErrorsArePropagated(boolean backtracking) {
        TspSolver solver = solver(backtracking, () -> { throw new AssertionError("Erro da JVM"); });

        assertThrows(NullPointerException.class, () -> solver.solve(null));
        assertThrows(AssertionError.class, () -> solver.solve(ContractTestProblems.triangle()));
    }

    private static void assertError(TspResult result) {
        assertEquals(SolverStatus.ERRO, result.status());
        assertFalse(result.optimalityGuaranteed());
    }

    private static TspSolver solver(boolean backtracking, LongSupplier clock) {
        return backtracking ? new BacktrackingSolver(clock) : new BruteForceSolver(clock);
    }

    private static Stream<Arguments> durations() {
        return Stream.of(false, true).flatMap(backtracking -> Stream.of(
                Arguments.of(backtracking, 0, 0, 0),
                Arguments.of(backtracking, -20, -5, 15),
                Arguments.of(backtracking, Long.MAX_VALUE - 5, Long.MIN_VALUE + 4, 10)));
    }
}
