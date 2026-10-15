package io.github.nathan1227n.nodewalker.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.solver.BruteForceSolver;
import io.github.nathan1227n.nodewalker.core.solver.SolverMetrics;
import io.github.nathan1227n.nodewalker.core.solver.SolverStatus;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.solver.TspSolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BenchmarkRunnerTest {
    @Test
    void warmsUpWithoutReadingTheClockAndMeasuresOnlySolveOnTheSameProblem() {
        TspProblem problem = TestProblems.triangle();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        List<String> events = new ArrayList<>();
        long[] clock = {100, 109, 200, 201, 300, 305};
        TspSolver solver = input -> {
            assertSame(problem, input);
            events.add("solve");
            SolverMetrics metrics = new SolverMetrics(OptionalLong.of(calls.incrementAndGet()),
                    OptionalLong.of(2), OptionalLong.empty(), Optional.empty());
            return TestProblems.completed(input, List.of(0, 1, 2, 0), metrics);
        };
        BenchmarkRunner runner = new BenchmarkRunner(() -> {
            events.add("clock");
            return clock[ticks.getAndIncrement()];
        });

        BenchmarkRunner.Report report = runner.run(problem, solver, 2, 3);

        assertEquals(5, calls.get());
        assertEquals(6, ticks.get());
        assertEquals(List.of("solve", "solve", "clock", "solve", "clock", "clock", "solve", "clock",
                "clock", "solve", "clock"), events);
        assertArrayEquals(new long[] {9, 1, 5}, report.durationsNanos());
        assertEquals(new BigDecimal("5"), report.medianNanos());
        assertEquals(3, report.result().metrics().elapsedNanos().orElseThrow());
        assertEquals(2, report.result().metrics().completeRoutesEvaluated().orElseThrow());
    }

    @Test
    void supportsNoWarmupAndOneMeasurementWithoutMutatingMetrics() {
        TspProblem problem = TestProblems.triangle();
        TspResult result = TestProblems.completed(problem);
        AtomicInteger tick = new AtomicInteger();

        BenchmarkRunner.Report report = new BenchmarkRunner(() -> tick.getAndAdd(7))
                .run(problem, ignored -> result, 0, 1);

        assertSame(result, report.result());
        assertEquals(new BigDecimal("7"), report.medianNanos());
        assertTrue(report.result().metrics().elapsedNanos().isEmpty());
        long[] accessed = report.durationsNanos();
        accessed[0] = 99;
        assertArrayEquals(new long[] {7}, report.durationsNanos());
    }

    @ParameterizedTest
    @CsvSource({"-30, -15, 15", "9223372036854775804, -9223372036854775805, 7"})
    void usesElapsedDifferencesRatherThanAbsoluteNanoTimeValues(long start, long end, long expected) {
        AtomicInteger tick = new AtomicInteger();
        TspProblem problem = TestProblems.triangle();

        BenchmarkRunner.Report report = new BenchmarkRunner(() -> tick.getAndIncrement() == 0 ? start : end)
                .run(problem, TestProblems::completed, 0, 1);

        assertArrayEquals(new long[] {expected}, report.durationsNanos());
    }

    @Test
    void rejectsAnInvalidNegativeDuration() {
        TspProblem problem = TestProblems.triangle();
        AtomicInteger clock = new AtomicInteger(2);

        BenchmarkRunner.Failure failure = assertThrows(BenchmarkRunner.Failure.class,
                () -> new BenchmarkRunner(clock::getAndDecrement).run(problem, TestProblems::completed, 0, 1));

        assertTrue(failure.getMessage().contains("duração negativa"));
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"ERRO", "CANCELADO", "LIMITE_ATINGIDO"})
    void refusesEveryNonCompletedTerminalStatusEvenWhenRepeatedConsistently(SolverStatus status) {
        TspProblem problem = TestProblems.triangle();
        AtomicInteger calls = new AtomicInteger();
        TspResult interrupted = TspResult.interrupted(problem, status, Optional.empty(), OptionalLong.empty(),
                SolverMetrics.empty(), "Motivo original");

        BenchmarkRunner.Failure failure = assertThrows(BenchmarkRunner.Failure.class,
                () -> new BenchmarkRunner(() -> 0).run(problem, ignored -> {
                    calls.incrementAndGet();
                    return interrupted;
                }, 0, 5));

        assertEquals(1, calls.get());
        assertSame(interrupted, failure.result());
        assertTrue(failure.getMessage().contains(status.toString()));
        assertTrue(failure.getMessage().contains("Motivo original"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void abortsOnExceptionsInWarmupOrMeasurementWithoutMaskingTheCause(boolean warmup) {
        TspProblem problem = TestProblems.triangle();
        IllegalStateException cause = new IllegalStateException("Falha real");
        AtomicInteger calls = new AtomicInteger();
        BenchmarkRunner.Failure failure = assertThrows(BenchmarkRunner.Failure.class,
                () -> new BenchmarkRunner(() -> 0).run(problem, ignored -> {
                    calls.incrementAndGet();
                    throw cause;
                }, warmup ? 1 : 0, 5));

        assertEquals(1, calls.get());
        assertSame(cause, failure.getCause());
        assertTrue(failure.getMessage().contains(warmup ? "Aquecimento 1" : "Medição 1"));
        assertTrue(failure.getMessage().contains("IllegalStateException: Falha real"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsNullResultsInWarmupOrMeasurement(boolean warmup) {
        BenchmarkRunner.Failure failure = assertThrows(BenchmarkRunner.Failure.class,
                () -> new BenchmarkRunner(() -> 0).run(TestProblems.triangle(), ignored -> null, warmup ? 1 : 0, 1));

        assertTrue(failure.getMessage().contains("retornou null"));
    }

    @ParameterizedTest
    @CsvSource({"true, 0", "true, 1", "true, 2", "false, 0", "false, 1", "false, 2"})
    void comparesCostAndRouteAcrossAllCallsAndStopsAtTheFirstMismatch(boolean tied, int warmup) {
        TspProblem problem = TestProblems.fourCities(tied);
        TspResult first = TestProblems.completed(problem, List.of(0, 1, 2, 3, 0), SolverMetrics.empty());
        TspResult second = TestProblems.completed(problem, List.of(0, 1, 3, 2, 0), SolverMetrics.empty());
        AtomicInteger calls = new AtomicInteger();
        BenchmarkRunner.Failure failure = assertThrows(BenchmarkRunner.Failure.class,
                () -> new BenchmarkRunner(() -> 0).run(problem,
                        ignored -> calls.getAndIncrement() == 0 ? first : second, warmup, 5));

        assertEquals(2, calls.get());
        assertSame(second, failure.result());
        assertTrue(failure.getMessage().contains("inconsistente"));
        assertTrue(failure.getMessage().contains("Esperado estado=CONCLUIDO"));
        assertTrue(failure.getMessage().contains(warmup == 2 ? "Aquecimento 2" : "Medição"));
    }

    @Test
    void doesNotReturnAPartialMedianWhenALaterMeasuredRunFails() {
        TspProblem problem = TestProblems.triangle();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(BenchmarkRunner.Failure.class, () -> new BenchmarkRunner(() -> 0).run(problem, ignored -> {
            if (calls.incrementAndGet() == 1) {
                return TestProblems.completed(problem);
            }
            return TspResult.interrupted(problem, SolverStatus.ERRO, Optional.empty(), OptionalLong.empty(),
                    SolverMetrics.empty(), "Falha depois de uma medição válida");
        }, 0, 5));
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @CsvSource({"3, 0", "4, 657", "5, -1", "6, -9223372036854775808"})
    void repeatsActualSolverResultsOnGeneratedInstances(int n, long seed) {
        TspProblem problem = TspGenerator.generate(n, seed);
        BenchmarkRunner runner = new BenchmarkRunner(System::nanoTime);

        BenchmarkRunner.Report first = runner.run(problem, new BruteForceSolver(), 2, 3);
        BenchmarkRunner.Report second = runner.run(problem, new BruteForceSolver(), 0, 2);

        assertEquals(first.result().route(), second.result().route());
        assertEquals(first.result().cost(), second.result().cost());
        assertEquals(SolverStatus.CONCLUIDO, first.result().status());
        assertEquals(3, first.durationsNanos().length);
        assertEquals(2, second.durationsNanos().length);
    }

    @ParameterizedTest
    @CsvSource({"-1, 1", "0, 0", "0, -1"})
    void rejectsInvalidIterationCounts(int warmup, int runs) {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkRunner(() -> 0)
                .run(TestProblems.triangle(), TestProblems::completed, warmup, runs));
    }

    @Test
    void doesNotCaptureJvmErrorsAsSuccessfulOrOrdinaryMeasurements() {
        AssertionError cause = new AssertionError("Error preservado");

        assertSame(cause, assertThrows(AssertionError.class, () -> new BenchmarkRunner(() -> 0)
                .run(TestProblems.triangle(), ignored -> { throw cause; }, 0, 1)));
    }
}
