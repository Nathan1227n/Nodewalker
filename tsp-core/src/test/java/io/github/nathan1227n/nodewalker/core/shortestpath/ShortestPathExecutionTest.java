package io.github.nathan1227n.nodewalker.core.shortestpath;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShortestPathExecutionTest {
    @ParameterizedTest
    @CsvSource({"0,LIMITE_ATINGIDO,0", "1,LIMITE_ATINGIDO,1", "2,LIMITE_ATINGIDO,2", "3,CONCLUIDO,3", "4,CONCLUIDO,3"})
    void vertexBudgetAdmitsExactlyKAndNaturalCompletionAtK(long budget, ShortestPathStatus status, long count) {
        var result = solver().solve(ShortestPathTestGraphs.chain(),
                ShortestPathExecutionOptions.limits(budget, 100, 1000), new ShortestPathCancellationToken());
        assertEquals(status, result.status());
        assertEquals(count, result.metrics().processedVertices());
        for (int v = 0; v < 3; v++) {
            assertEquals(v < count ? ShortestPathResult.VertexState.CONFIRMADO
                    : ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(v));
        }
        if (budget == 0) assertEquals(0, result.metrics().peakQueueSize());
    }

    @ParameterizedTest
    @CsvSource({"0,LIMITE_ATINGIDO,1", "1,LIMITE_ATINGIDO,2", "2,LIMITE_ATINGIDO,2", "3,LIMITE_ATINGIDO,3", "4,CONCLUIDO,3", "5,CONCLUIDO,3"})
    void edgeBudgetCountsActualAdjacencyIncludingFixedNeighbors(long budget, ShortestPathStatus status, long vertices) {
        var result = solver().solve(ShortestPathTestGraphs.chain(),
                ShortestPathExecutionOptions.limits(100, budget, 1000), new ShortestPathCancellationToken());
        assertEquals(status, result.status());
        assertEquals(Math.min(budget, 4), result.metrics().examinedEdges());
        assertEquals(vertices, result.metrics().processedVertices());
    }

    @Test
    void isolatedOriginCompletesWithVertexBudgetOneAndEdgeBudgetZero() {
        var graph = new RefugeGraph(ContractTestProblems.equalEdges(3, 1), RefugeTopology.manual(3, List.of()));
        var result = solver().solve(graph, ShortestPathExecutionOptions.limits(1, 0, 1000), new ShortestPathCancellationToken());
        assertEquals(ShortestPathStatus.CONCLUIDO, result.status());
        assertEquals(ShortestPathResult.VertexState.INALCANCAVEL, result.vertexState(1));
    }

    @Test
    void tentativeLabelsAreNeverPublishedAfterCancellation() {
        var graph = new RefugeGraph(ContractTestProblems.equalEdges(3, 1), RefugeTopology.complete(3));
        var token = new ShortestPathCancellationToken();
        var solver = new DijkstraShortestPathSolver(() -> 0, (phase, vertices, edges) -> {
            if (phase == DijkstraShortestPathSolver.Phase.BEFORE_VERTEX && vertices == 1) token.cancel();
        });
        var result = solver.solve(graph, DijkstraShortestPathSolverTest.generous(), token);
        assertEquals(ShortestPathStatus.CANCELADO, result.status());
        assertEquals(2, result.metrics().successfulRelaxations()); // both tentative labels existed
        assertEquals(BigInteger.ZERO, result.distanceTo(0).orElseThrow());
        for (int v : new int[] {1, 2}) {
            assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(v));
            assertTrue(result.distanceTo(v).isEmpty());
            assertTrue(result.pathTo(v).isEmpty());
            assertTrue(result.predecessorOf(v).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(DijkstraShortestPathSolver.Phase.class)
    void cancellationAtEveryPhaseNeverBecomesCompletion(DijkstraShortestPathSolver.Phase target) {
        var token = new ShortestPathCancellationToken();
        var solver = new DijkstraShortestPathSolver(() -> 0, (phase, vertices, edges) -> {
            if (phase == target) token.cancel();
        });
        var result = solver.solve(ShortestPathTestGraphs.chain(), DijkstraShortestPathSolverTest.generous(), token);
        assertEquals(ShortestPathStatus.CANCELADO, result.status());
        assertFalse(result.allDistancesResolved());
        assertTrue(result.reason().orElseThrow().contains(target.name()));
        for (int v = 0; v < 3; v++) {
            if (result.vertexState(v) == ShortestPathResult.VertexState.CONFIRMADO) {
                assertEquals(FloydWarshallOracle.distances(ShortestPathTestGraphs.chain())[0][v], result.distanceTo(v).orElseThrow());
            } else assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(v));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {999_999, 1_000_000, 1_000_001})
    void exactTimeBoundaryUsesGreaterThanOrEqual(long nanos) {
        var clock = new AtomicLong();
        var solver = new DijkstraShortestPathSolver(clock::get, (phase, vertices, edges) -> clock.set(nanos));
        var result = solver.solve(ShortestPathTestGraphs.chain(),
                ShortestPathExecutionOptions.limits(100, 100, 1), new ShortestPathCancellationToken());
        assertEquals(nanos < 1_000_000 ? ShortestPathStatus.CONCLUIDO : ShortestPathStatus.LIMITE_ATINGIDO, result.status());
        assertEquals(nanos, result.metrics().elapsedNanos().orElseThrow());
    }

    @Test
    void timeZeroAndPreCancelledTokenAvoidExploration() {
        var token = new ShortestPathCancellationToken();
        token.cancel(); token.cancel();
        var result = solver().solve(ShortestPathTestGraphs.chain(),
                ShortestPathExecutionOptions.limits(0, 0, 0), token);
        assertEquals(ShortestPathStatus.CANCELADO, result.status());
        assertEquals(0, result.metrics().processedVertices());
        assertEquals(0, result.metrics().peakQueueSize());
        assertTrue(result.pathTo(0).isEmpty());
        assertTrue(token.isCancellationRequested());
        assertEquals(ShortestPathStatus.LIMITE_ATINGIDO, solver().solve(ShortestPathTestGraphs.chain(),
                ShortestPathExecutionOptions.limits(100, 100, 0), new ShortestPathCancellationToken()).status());
    }

    @Test
    void midRunTimeLimitPreservesOnlyFixedDistances() {
        var clock = new AtomicLong();
        var solver = new DijkstraShortestPathSolver(clock::get, (phase, vertices, edges) -> {
            if (vertices == 2) clock.set(1_000_000);
        });
        var result = solver.solve(ShortestPathTestGraphs.chain(),
                ShortestPathExecutionOptions.limits(100, 100, 1), new ShortestPathCancellationToken());
        assertEquals(ShortestPathStatus.LIMITE_ATINGIDO, result.status());
        assertEquals(List.of(0, 1), result.pathTo(1).orElseThrow());
        assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(2));
    }

    @Test
    void errorPreservesConfirmedPathsAndDoesNotClassifyUnreachable() {
        var solver = new DijkstraShortestPathSolver(() -> 0, (phase, vertices, edges) -> {
            if (vertices == 2) throw new IllegalStateException("injected execution failure");
        });
        var result = solver.solve(ShortestPathTestGraphs.chain(), DijkstraShortestPathSolverTest.generous(), new ShortestPathCancellationToken());
        assertEquals(ShortestPathStatus.ERRO, result.status());
        assertEquals(List.of(0, 1), result.pathTo(1).orElseThrow());
        assertTrue(result.reason().orElseThrow().contains("injected execution failure"));
        assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(2));
    }

    @Test
    void actualClockFailureIsErrorEvenWithCancelledTokenAndDoesNotInventTime() {
        var token = new ShortestPathCancellationToken(); token.cancel();
        var solver = new DijkstraShortestPathSolver(() -> { throw new IllegalStateException("clock failed"); }, null);
        var result = solver.solve(ShortestPathTestGraphs.chain(), DijkstraShortestPathSolverTest.generous(), token);
        assertEquals(ShortestPathStatus.ERRO, result.status());
        assertTrue(result.metrics().elapsedNanos().isEmpty());
    }

    @Test
    void durationHandlesNanoTimeWrapAndRejectsNegativeDuration() {
        var ticks = new AtomicLong(Long.MAX_VALUE);
        var solver = new DijkstraShortestPathSolver(ticks::get, (phase, vertices, edges) -> ticks.set(Long.MIN_VALUE));
        var result = solver.solve(ShortestPathTestGraphs.chain(), DijkstraShortestPathSolverTest.generous(), new ShortestPathCancellationToken());
        assertEquals(ShortestPathStatus.CONCLUIDO, result.status());
        assertEquals(1, result.metrics().elapsedNanos().orElseThrow());
        var bad = new AtomicLong(10);
        var negative = new DijkstraShortestPathSolver(bad::get, (phase, vertices, edges) -> bad.set(9));
        assertEquals(ShortestPathStatus.ERRO, negative.solve(ShortestPathTestGraphs.chain()).status());
    }

    @Test
    void threadInterruptionIsObservedWithoutClearingItsSignal() {
        Thread.currentThread().interrupt();
        try {
            var result = solver().solve(ShortestPathTestGraphs.chain());
            assertEquals(ShortestPathStatus.CANCELADO, result.status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test
    void cancellationFromAnotherThreadUsesARealBarrier() throws Exception {
        var reached = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var token = new ShortestPathCancellationToken();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var solver = new DijkstraShortestPathSolver(() -> 0, (phase, vertices, edges) -> {
                if (phase == DijkstraShortestPathSolver.Phase.BEFORE_VERTEX && vertices == 1) {
                    reached.countDown();
                    try {
                        if (!resume.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            });
            var future = executor.submit(() -> solver.solve(ShortestPathTestGraphs.chain(),
                    DijkstraShortestPathSolverTest.generous(), token));
            assertTrue(reached.await(5, TimeUnit.SECONDS));
            token.cancel(); resume.countDown();
            var result = future.get(5, TimeUnit.SECONDS);
            assertEquals(ShortestPathStatus.CANCELADO, result.status());
            assertEquals(1, result.metrics().processedVertices());
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    private static DijkstraShortestPathSolver solver() { return new DijkstraShortestPathSolver(() -> 0, null); }
}
