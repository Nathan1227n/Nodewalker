package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.SearchSpace;
import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class SolverExecutionControlsTest {
    @ParameterizedTest
    @MethodSource("nodeBoundaries")
    void onlyAttemptingTheNextStateExceedsTheInclusiveNodeBudget(boolean backtracking, int offset) {
        long completeNodes = backtracking ? 5 : 2;
        TspResult result = solver(backtracking, () -> 0, null).solve(ContractTestProblems.triangle(),
                SolverExecutionOptions.nodeLimit(completeNodes + offset), new SolverCancellationToken());

        assertEquals(offset < 0 ? SolverStatus.LIMITE_ATINGIDO : SolverStatus.CONCLUIDO, result.status());
        assertEquals(offset >= 0, result.optimalityGuaranteed());
        assertEquals(offset < 0 ? completeNodes - 1 : completeNodes, result.metrics().visitedNodes().orElseThrow());
        assertEquals(offset < 0 ? 1 : 2, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(45, result.cost().orElseThrow());
        assertEquals(BigInteger.ZERO, result.metrics().discardedRoutes().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("zeroLimits")
    void zeroBudgetStopsBeforeAnyStateWithoutInventingAnIncumbent(boolean backtracking, boolean time) {
        TspResult result = solver(backtracking, () -> -10, null).solve(ContractTestProblems.triangle(),
                time ? SolverExecutionOptions.timeLimitMillis(0) : SolverExecutionOptions.nodeLimit(0),
                new SolverCancellationToken());
        assertInterrupted(result, SolverStatus.LIMITE_ATINGIDO);
        assertTrue(result.route().isEmpty());
        assertTrue(result.cost().isEmpty());
        assertCounts(result, 0, 0, BigInteger.ZERO);
        assertEquals(0, result.metrics().elapsedNanos().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preCancelledTokenIsPersistentAndIndependentOfOtherTokens(boolean backtracking) {
        var token = new SolverCancellationToken();
        token.cancel();
        token.cancel();
        TspSolver solver = solver(backtracking, () -> 0, null);
        for (int i = 0; i < 2; i++) {
            TspResult result = solver.solve(ContractTestProblems.triangle(), SolverExecutionOptions.nodeLimit(0), token);
            assertInterrupted(result, SolverStatus.CANCELADO);
            assertCounts(result, 0, 0, BigInteger.ZERO);
            assertTrue(result.route().isEmpty());
        }
        assertTrue(token.isCancellationRequested());
        assertEquals(SolverStatus.CONCLUIDO, solver.solve(ContractTestProblems.triangle(),
                SolverExecutionOptions.unlimited(), new SolverCancellationToken()).status());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationDuringTheFirstStateDoesNotPublishAnIncompleteCycle(boolean backtracking) {
        var token = new SolverCancellationToken();
        TspSolver solver = solver(backtracking, () -> 0, (phase, nodes, evaluated) -> {
            if (nodes == 1) {
                token.cancel();
            }
        });
        TspResult result = solver.solve(ContractTestProblems.triangle(), SolverExecutionOptions.unlimited(), token);
        assertInterrupted(result, SolverStatus.CANCELADO);
        assertCounts(result, 1, 0, BigInteger.ZERO);
        assertTrue(result.route().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nodeLimitPreservesAnEntireIncumbentIncludingTheReturnEdge(boolean backtracking) {
        var problem = ContractTestProblems.triangle();
        TspResult result = solver(backtracking, () -> 0, null).solve(problem,
                SolverExecutionOptions.nodeLimit(backtracking ? 3 : 1), new SolverCancellationToken());
        assertInterrupted(result, SolverStatus.LIMITE_ATINGIDO);
        assertCounts(result, backtracking ? 3 : 1, 1, BigInteger.ZERO);
        assertEquals(List.of(0, 1, 2, 0), result.route().orElseThrow().indices());
        assertEquals(45, RouteValidator.validateAndCost(problem, result.route().orElseThrow()));
        assertEquals(45, result.cost().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationWhileProcessingALaterStateKeepsTheEarlierIncumbent(boolean backtracking) {
        var token = new SolverCancellationToken();
        long firstCycleNodes = backtracking ? 3 : 1;
        TspResult result = solver(backtracking, () -> 0, (phase, nodes, evaluated) -> {
            if (nodes > firstCycleNodes && evaluated == 1) {
                token.cancel();
            }
        }).solve(ContractTestProblems.triangle(), SolverExecutionOptions.unlimited(), token);
        assertInterrupted(result, SolverStatus.CANCELADO);
        assertCounts(result, firstCycleNodes + 1, 1, BigInteger.ZERO);
        assertEquals(45, result.cost().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void longMaximumIsAValidPartialIncumbent(boolean backtracking) {
        var problem = ContractTestProblems.triangle(Long.MAX_VALUE, 0, 0);
        TspResult result = solver(backtracking, () -> 0, null).solve(problem,
                SolverExecutionOptions.nodeLimit(backtracking ? 3 : 1), new SolverCancellationToken());
        assertInterrupted(result, SolverStatus.LIMITE_ATINGIDO);
        assertEquals(Long.MAX_VALUE, result.cost().orElseThrow());
        assertEquals(Long.MAX_VALUE, RouteValidator.validateAndCost(problem, result.route().orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aNodeBudgetBeforeTheFirstCycleDoesNotCountUnexploredRoutesAsDiscarded(boolean backtracking) {
        TspResult result = solver(backtracking, () -> 0, null).solve(ContractTestProblems.triangle(),
                SolverExecutionOptions.nodeLimit(backtracking ? 2 : 0), new SolverCancellationToken());
        assertInterrupted(result, SolverStatus.LIMITE_ATINGIDO);
        assertCounts(result, backtracking ? 2 : 0, 0, BigInteger.ZERO);
        assertTrue(result.route().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesOnlyActualPrunedLeavesOnLimitOrCancellation(boolean cancel) {
        var token = new SolverCancellationToken();
        TspResult result = solver(true, () -> 0, (phase, nodes, evaluated) -> {
            if (cancel && nodes == 5) {
                token.cancel();
            }
        }).solve(zeroProblem(4), SolverExecutionOptions.nodeLimit(5), token);
        assertInterrupted(result, cancel ? SolverStatus.CANCELADO : SolverStatus.LIMITE_ATINGIDO);
        assertCounts(result, 5, 1, BigInteger.ONE);
        assertEquals(0, result.cost().orElseThrow());
        assertTrue(BigInteger.TWO.compareTo(SearchSpace.completeRoutes(4)) < 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeLimitPreservesTheFirstCompleteCycle(boolean backtracking) {
        AtomicLong clock = new AtomicLong(-20);
        TspResult result = solver(backtracking, clock::get, (phase, nodes, evaluated) -> {
            if (evaluated == 1) {
                clock.set(999_980); // exatamente 1 ms depois da origem negativa
            }
        }).solve(ContractTestProblems.triangle(), SolverExecutionOptions.timeLimitMillis(1), new SolverCancellationToken());
        assertInterrupted(result, SolverStatus.LIMITE_ATINGIDO);
        assertEquals(45, result.cost().orElseThrow());
        assertEquals(1, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(1_000_000, result.metrics().elapsedNanos().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("timeBoundaries")
    void timeExpiresAtEqualityAndSupportsNegativeOriginsAndClockWrap(boolean backtracking, long start, long elapsed) {
        AtomicInteger calls = new AtomicInteger();
        TspResult result = solver(backtracking, () -> calls.getAndIncrement() == 0 ? start : start + elapsed, null)
                .solve(ContractTestProblems.triangle(), SolverExecutionOptions.timeLimitMillis(1),
                        new SolverCancellationToken());
        assertEquals(elapsed < 1_000_000 ? SolverStatus.CONCLUIDO : SolverStatus.LIMITE_ATINGIDO, result.status());
        assertEquals(elapsed, result.metrics().elapsedNanos().orElseThrow());
        if (elapsed >= 1_000_000) {
            assertCounts(result, 0, 0, BigInteger.ZERO);
            assertFalse(result.optimalityGuaranteed());
        }
    }

    @ParameterizedTest
    @MethodSource("priorities")
    void simultaneousConditionsHaveStablePriorityAndReportAllObservedCauses(boolean backtracking, boolean cancel) {
        var token = new SolverCancellationToken();
        AtomicLong clock = new AtomicLong();
        TspResult result = solver(backtracking, clock::get, (phase, nodes, evaluated) -> {
            if (nodes == 1 && (phase == SolverExecutionControl.Phase.BEFORE_NODE
                    || phase == SolverExecutionControl.Phase.BEFORE_DESCENT)) {
                clock.set(1_000_000);
                if (cancel) {
                    token.cancel();
                }
            }
        }).solve(ContractTestProblems.triangle(), SolverExecutionOptions.limits(1, 1), token);
        assertInterrupted(result, cancel ? SolverStatus.CANCELADO : SolverStatus.LIMITE_ATINGIDO);
        String reason = result.reason().orElseThrow();
        assertTrue(reason.startsWith("Interrupção por " + (cancel ? "cancelamento" : "tempo")));
        assertTrue(reason.contains("tempo >= 1 ms"));
        assertTrue(reason.contains("orçamento de 1 nós esgotado"));
        assertEquals(cancel, reason.contains("token cancelado"));
        assertEquals(1, result.metrics().visitedNodes().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realCheckpointFailuresAreNeverReclassifiedAsCancellationOrLimit(boolean backtracking) {
        var token = new SolverCancellationToken();
        TspResult result = solver(backtracking, () -> 0, (phase, nodes, evaluated) -> {
            if (phase == SolverExecutionControl.Phase.EDGE) {
                token.cancel();
                throw new IllegalStateException("falha real");
            }
        }).solve(ContractTestProblems.triangle(), SolverExecutionOptions.nodeLimit(100), token);
        assertInterrupted(result, SolverStatus.ERRO);
        assertTrue(result.reason().orElseThrow().contains("falha real"));
        assertTrue(result.route().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observedOverflowWinsOverALaterCancellationAndExpiredClock(boolean backtracking) {
        var token = new SolverCancellationToken();
        AtomicInteger readsInCheckpoint = new AtomicInteger();
        TspResult result = solver(backtracking, () -> {
            if (readsInCheckpoint.getAndIncrement() == 0) {
                return 0;
            }
            token.cancel(); // leitura final ocorre após a falha aritmética
            return 2_000_000;
        }, (phase, nodes, evaluated) -> readsInCheckpoint.set(0))
                .solve(ContractTestProblems.triangle(Long.MAX_VALUE, 1, 0),
                        SolverExecutionOptions.limits(100, 1), token);
        assertInterrupted(result, SolverStatus.ERRO);
        assertTrue(result.reason().orElseThrow().contains("ArithmeticException"));
        assertEquals(0, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertTrue(token.isCancellationRequested());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exhaustedBudgetPreventsUnobservedCostArithmeticButAdmittedOverflowRemainsAnError(boolean backtracking) {
        var solver = solver(backtracking, () -> 0, null);
        var problem = ContractTestProblems.triangle(Long.MAX_VALUE, 1, 0);
        long beforeOverflow = backtracking ? 2 : 0;
        TspResult limited = solver.solve(problem, SolverExecutionOptions.nodeLimit(beforeOverflow),
                new SolverCancellationToken());
        assertInterrupted(limited, SolverStatus.LIMITE_ATINGIDO);
        assertCounts(limited, beforeOverflow, 0, BigInteger.ZERO);
        TspResult failed = solver.solve(problem, SolverExecutionOptions.nodeLimit(beforeOverflow + 1),
                new SolverCancellationToken());
        assertInterrupted(failed, SolverStatus.ERRO);
        assertTrue(failed.reason().orElseThrow().contains("ArithmeticException"));
        assertCounts(failed, backtracking ? 2 : 1, 0, BigInteger.ZERO);
    }

    @ParameterizedTest
    @MethodSource("controlledReferenceProblems")
    void boundedAndExplicitlyUnboundedCallsPreserveLegacyOrderCostsAndCounters(boolean backtracking, int n, long seed) {
        var solver = solver(backtracking, () -> 0, null);
        var problem = TspGenerator.generate(n, seed);
        var legacy = solver.solve(problem);
        for (var options : List.of(SolverExecutionOptions.nodeLimit(Long.MAX_VALUE), SolverExecutionOptions.unlimited())) {
            var result = solver.solve(problem, options, new SolverCancellationToken());
            assertEquals(SolverStatus.CONCLUIDO, result.status());
            assertEquals(legacy.route(), result.route());
            assertEquals(legacy.cost(), result.cost());
            assertEquals(legacy.metrics(), result.metrics());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clockFailureWhenCancellationIsPendingIsStillARealError(boolean backtracking) {
        var token = new SolverCancellationToken();
        AtomicInteger calls = new AtomicInteger();
        TspResult result = solver(backtracking, () -> {
            if (calls.getAndIncrement() == 0) {
                return 0;
            }
            throw new IllegalStateException("relógio falhou");
        }, (phase, nodes, evaluated) -> token.cancel()).solve(ContractTestProblems.triangle(),
                SolverExecutionOptions.timeLimitMillis(1), token);
        assertInterrupted(result, SolverStatus.ERRO);
        assertTrue(result.reason().orElseThrow().contains("relógio falhou"));
        assertTrue(result.metrics().elapsedNanos().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondaryReportingFailurePreservesTheAlreadyObservedCancellation(boolean backtracking) {
        AtomicInteger calls = new AtomicInteger();
        var token = new SolverCancellationToken();
        token.cancel();
        TspResult result = solver(backtracking, () -> {
            if (calls.getAndIncrement() == 0) {
                return 0;
            }
            throw new IllegalStateException("falha secundária");
        }, null).solve(ContractTestProblems.triangle(), SolverExecutionOptions.nodeLimit(100), token);
        assertInterrupted(result, SolverStatus.CANCELADO);
        assertTrue(result.reason().orElseThrow().contains("falha secundária"));
        assertCounts(result, 0, 0, BigInteger.ZERO);
        assertTrue(result.metrics().elapsedNanos().isEmpty());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @MethodSource("crossThreadCancellation")
    void anotherThreadCancelsAtADeterministicBarrierIncludingBeforeCompletion(boolean backtracking,
                                                                            boolean atFinalization, boolean interrupt)
            throws InterruptedException {
        var token = new SolverCancellationToken();
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var result = new AtomicReference<TspResult>();
        var failure = new AtomicReference<Throwable>();
        var preservedInterrupt = new AtomicBoolean();
        var paused = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                result.set(solver(backtracking, () -> 0, (phase, nodes, evaluated) -> {
                    boolean target = atFinalization ? phase == SolverExecutionControl.Phase.FINALIZATION : evaluated == 1;
                    if (target && paused.compareAndSet(false, true)) {
                        reached.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException interruption) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }).solve(ContractTestProblems.triangle(), SolverExecutionOptions.unlimited(), token));
                preservedInterrupt.set(Thread.currentThread().isInterrupted());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        }, "solver-cancellation-test");
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(reached.await(5, TimeUnit.SECONDS));
            if (interrupt) {
                worker.interrupt();
            } else {
                token.cancel();
                release.countDown();
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertInterrupted(result.get(), SolverStatus.CANCELADO);
            assertEquals(atFinalization ? 2 : 1, result.get().metrics().completeRoutesEvaluated().orElseThrow());
            assertEquals(45, result.get().cost().orElseThrow());
            assertEquals(interrupt, preservedInterrupt.get());
            assertEquals(!interrupt, token.isCancellationRequested());
        } finally {
            token.cancel();
            release.countDown();
            worker.interrupt();
            worker.join(5000);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aCancellationOrderedAfterCompletionCannotChangeThePublishedResult(boolean backtracking) throws InterruptedException {
        var token = new SolverCancellationToken();
        var finished = new CountDownLatch(1);
        var result = new AtomicReference<TspResult>();
        Thread worker = new Thread(() -> {
            try {
                result.set(solver(backtracking, () -> 0, null).solve(ContractTestProblems.triangle(),
                        SolverExecutionOptions.unlimited(), token));
            } finally {
                finished.countDown();
            }
        });
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            token.cancel();
            assertEquals(SolverStatus.CONCLUIDO, result.get().status());
            assertTrue(result.get().optimalityGuaranteed());
        } finally {
            token.cancel();
            worker.join(5000);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyCallsIgnoreThreadInterruptionAndStillReadTheClockExactlyTwice(boolean backtracking) {
        boolean previouslyInterrupted = Thread.currentThread().isInterrupted();
        AtomicInteger calls = new AtomicInteger();
        try {
            Thread.currentThread().interrupt();
            var solver = solver(backtracking, () -> calls.getAndIncrement(), null);
            assertEquals(SolverStatus.CONCLUIDO, solver.solve(ContractTestProblems.triangle()).status());
            assertEquals(2, calls.get());
            assertTrue(Thread.currentThread().isInterrupted());
            assertInterrupted(solver.solve(ContractTestProblems.triangle(), SolverExecutionOptions.unlimited(),
                    new SolverCancellationToken()), SolverStatus.CANCELADO);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            if (!previouslyInterrupted) {
                Thread.interrupted();
            }
        }
    }

    @Test
    void tsp13KeepsPartialCoverageSeparateFromCompletionAndDiscardCountsAboveLong() {
        var problem = zeroProblem(25);
        BigInteger previous = BigInteger.ZERO;
        for (int budget : new int[] {24, 25, 100, 250, 300, 301, 302}) {
            var result = new BacktrackingSolver(() -> 0).solve(problem,
                    SolverExecutionOptions.nodeLimit(budget), new SolverCancellationToken());
            BigInteger expectedDiscarded = SolverAuditAssertions.zeroCostDiscardedRoutes(25, budget);
            var details = SolverAuditAssertions.diagnostics("TSP-13, maxVisitedNodes=" + budget, problem,
                    () -> "Referência analítica: ótimo=0, 24!=620448401733239439360000, descartes=" + expectedDiscarded,
                    Map.of("BacktrackingSolver", result));
            assertEquals(expectedDiscarded, result.metrics().discardedRoutes().orElseThrow(), details);
            assertEquals(new BigInteger("620448401733239439360000"), SolverAuditAssertions.factorial(24), details);
            SolverAuditAssertions.assertIncumbent(problem, BigInteger.ZERO, result, details);
            assertEquals(budget < 301 ? SolverStatus.LIMITE_ATINGIDO : SolverStatus.CONCLUIDO, result.status(), details);
            assertEquals(Math.min(budget, 301), result.metrics().visitedNodes().orElseThrow(), details);
            BigInteger discarded = result.metrics().discardedRoutes().orElseThrow();
            assertTrue(discarded.compareTo(previous) >= 0, details);
            previous = discarded;
            BigInteger covered = discarded.add(BigInteger.valueOf(result.metrics().completeRoutesEvaluated().orElseThrow()));
            if (budget < 301) {
                assertFalse(result.optimalityGuaranteed(), details);
                assertTrue(covered.compareTo(SearchSpace.completeRoutes(25)) < 0, details);
            } else {
                assertEquals(new BigInteger("620448401733239439360000"), covered, details);
                assertTrue(result.optimalityGuaranteed(), details);
            }
            if (budget >= 250) {
                assertTrue(discarded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0, details);
            }
            if (budget >= 25) {
                assertEquals(0, RouteValidator.validateAndCost(problem, result.route().orElseThrow()), details);
                assertEquals(1, result.metrics().completeRoutesEvaluated().orElseThrow(), details);
            } else {
                assertTrue(result.route().isEmpty(), details);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void controlledPolicyRefusalDoesNotApplyToTheLegacyEntry(boolean backtracking) {
        AtomicInteger calls = new AtomicInteger();
        var solver = solver(backtracking, () -> calls.getAndIncrement(), null);
        var policy = new SolverExecutionPolicy(3, 3, "teste");
        assertThrows(IllegalArgumentException.class, () -> solver.solve(zeroProblem(4),
                SolverExecutionOptions.unlimited().withPolicy(policy), new SolverCancellationToken()));
        assertEquals(0, calls.get());
        assertEquals(SolverStatus.CONCLUIDO, solver.solve(zeroProblem(4)).status());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsNullControlledArgumentsBeforeReadingClock(boolean backtracking) {
        var solver = solver(backtracking, () -> { throw new AssertionError("não ler"); }, null);
        assertThrows(NullPointerException.class, () -> solver.solve(null, SolverExecutionOptions.unlimited(), new SolverCancellationToken()));
        assertThrows(NullPointerException.class, () -> solver.solve(ContractTestProblems.triangle(), null, new SolverCancellationToken()));
        assertThrows(NullPointerException.class, () -> solver.solve(ContractTestProblems.triangle(), SolverExecutionOptions.unlimited(), null));
        assertThrows(AssertionError.class, () -> solver.solve(ContractTestProblems.triangle(),
                SolverExecutionOptions.unlimited(), new SolverCancellationToken()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void generationAndSearchHaveCompletelySeparateCancellationLifetimes(boolean backtracking) {
        var generation = new GenerationOptions(100, 100_000);
        var searchToken = new SolverCancellationToken();
        searchToken.cancel();
        var problem = TspGenerator.generate(4, 657, generation);
        generation.cancellationToken().cancel();
        var solver = solver(backtracking, () -> 0, null);
        assertEquals(SolverStatus.CONCLUIDO, solver.solve(problem,
                SolverExecutionOptions.unlimited(), new SolverCancellationToken()).status());
        assertInterrupted(solver.solve(problem, SolverExecutionOptions.unlimited(), searchToken), SolverStatus.CANCELADO);
    }

    @Test
    void legacyFunctionalImplementationsRejectUnsupportedControlsWithoutExecuting() {
        TspSolver legacy = problem -> { throw new AssertionError("não delegar controles para chamada legada"); };
        assertThrows(UnsupportedOperationException.class, () -> legacy.solve(ContractTestProblems.triangle(),
                SolverExecutionOptions.nodeLimit(1), new SolverCancellationToken()));
    }

    private static void assertInterrupted(TspResult result, SolverStatus status) {
        assertNotNull(result);
        assertEquals(status, result.status());
        assertFalse(result.optimalityGuaranteed());
        assertTrue(result.reason().isPresent());
    }

    private static void assertCounts(TspResult result, long nodes, long evaluated, BigInteger discarded) {
        assertEquals(nodes, result.metrics().visitedNodes().orElseThrow());
        assertEquals(evaluated, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(discarded, result.metrics().discardedRoutes().orElseThrow());
    }

    private static TspSolver solver(boolean backtracking, LongSupplier clock, SolverExecutionControl.Observer observer) {
        return backtracking ? new BacktrackingSolver(clock, observer) : new BruteForceSolver(clock, observer);
    }

    private static TspProblem zeroProblem(int n) {
        return new TspProblem(IntStream.range(0, n).mapToObj(i -> new City(i, 0, 0, 0)).toList(), new long[n][n]);
    }

    static Stream<Arguments> nodeBoundaries() {
        return Stream.of(false, true).flatMap(bt -> IntStream.rangeClosed(-1, 1).mapToObj(offset -> Arguments.of(bt, offset)));
    }

    static Stream<Arguments> zeroLimits() {
        return Stream.of(false, true).flatMap(bt -> Stream.of(false, true).map(time -> Arguments.of(bt, time)));
    }

    static Stream<Arguments> timeBoundaries() {
        return Stream.of(false, true).flatMap(bt -> Stream.of(-20L, Long.MAX_VALUE - 50)
                .flatMap(start -> Stream.of(999_999L, 1_000_000L, 1_000_001L)
                        .map(elapsed -> Arguments.of(bt, start, elapsed))));
    }

    static Stream<Arguments> priorities() {
        return Stream.of(false, true).flatMap(bt -> Stream.of(false, true).map(cancel -> Arguments.of(bt, cancel)));
    }

    static Stream<Arguments> crossThreadCancellation() {
        return Stream.of(false, true).flatMap(bt -> Stream.of(false, true).flatMap(last ->
                Stream.of(false, true).map(interrupt -> Arguments.of(bt, last, interrupt))));
    }

    static Stream<Arguments> controlledReferenceProblems() {
        return Stream.of(false, true).flatMap(bt -> IntStream.rangeClosed(3, 7).boxed().flatMap(n ->
                Stream.of(0L, 1L, 657L).map(seed -> Arguments.of(bt, n, seed))));
    }
}
