package io.github.nathan1227n.nodewalker.core.shortestpath;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ShortestPathContractsTest {
    @Test
    void metadataPreflightSeparatesWorkspaceTopologyAndMatrixAndRefusesHugeRequests() {
        var options = ShortestPathExecutionOptions.demonstration();
        var report = ShortestPathPreflight.analyze(ShortestPathTestGraphs.chain(), options);
        assertEquals(BigInteger.valueOf(256 * 3 + 1_048_576), report.solverEstimatedBytes());
        assertEquals(BigInteger.valueOf(72), report.matrixPayloadBytes());
        assertEquals(2, report.undirectedEdges());
        assertTrue(report.executionAllowed());
        assertThrows(UnsupportedOperationException.class, () -> report.warnings().clear());
        var huge = ShortestPathPreflight.analyze(RefugeTopology.complete(Integer.MAX_VALUE), options);
        assertFalse(huge.executionAllowed());
        assertTrue(huge.matrixPayloadBytes().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertThrows(IllegalArgumentException.class, huge::requireAllowed);
    }

    @Test
    void exactMemoryBudgetIsInclusiveAndRefusalPrecedesExecutionClock() {
        var graph = ShortestPathTestGraphs.chain();
        long estimate = ShortestPathPreflight.analyze(graph, ShortestPathExecutionOptions.demonstration())
                .solverEstimatedBytes().longValueExact();
        var exact = ShortestPathExecutionOptions.demonstration().withMemoryBudget(estimate);
        assertTrue(ShortestPathPreflight.analyze(graph, exact).executionAllowed());
        var tooSmall = exact.withMemoryBudget(estimate - 1);
        var solver = new DijkstraShortestPathSolver(() -> { throw new AssertionError("Must refuse before clock"); }, null);
        assertThrows(IllegalArgumentException.class, () -> solver.solve(graph, tooSmall, new ShortestPathCancellationToken()));
    }

    @Test
    void invalidArgumentsAndBudgetsFailBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> ShortestPathExecutionOptions.limits(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ShortestPathExecutionOptions.limits(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> ShortestPathExecutionOptions.limits(1, 1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> ShortestPathExecutionOptions.limits(1, 1, Long.MAX_VALUE / 1_000_000 + 1));
        assertThrows(IllegalArgumentException.class, () -> ShortestPathExecutionOptions.demonstration().withMemoryBudget(0));
        assertDoesNotThrow(() -> ShortestPathExecutionOptions.limits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE / 1_000_000));
        var solver = new DijkstraShortestPathSolver();
        var graph = ShortestPathTestGraphs.chain();
        assertThrows(NullPointerException.class, () -> solver.solve(null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> solver.solve(graph, -1, ShortestPathExecutionOptions.demonstration(), new ShortestPathCancellationToken()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> solver.solve(graph, 3, ShortestPathExecutionOptions.demonstration(), new ShortestPathCancellationToken()));
        assertThrows(NullPointerException.class, () -> solver.solve(graph, null, new ShortestPathCancellationToken()));
        assertThrows(NullPointerException.class, () -> solver.solve(graph, ShortestPathExecutionOptions.demonstration(), null));
    }

    @Test
    void validatedSnapshotCopiesArraysAndRejectsCyclicPredecessors() {
        var graph = ShortestPathTestGraphs.chain();
        BigInteger[] labels = {BigInteger.ZERO, BigInteger.valueOf(2), BigInteger.valueOf(5)};
        int[] parents = {-1, 0, 1};
        int[] ranks = {1, 2, 3};
        var metrics = new ShortestPathMetrics(OptionalLong.of(0), 3, 4, 2, 2, 1);
        var result = ShortestPathResult.create(graph, 0, ShortestPathStatus.CONCLUIDO, labels, parents, ranks, metrics, null);
        labels[2] = BigInteger.ZERO; parents[2] = 0; ranks[2] = 0;
        assertEquals(List.of(0, 1, 2), result.pathTo(2).orElseThrow());
        assertEquals(BigInteger.valueOf(5), result.distanceTo(2).orElseThrow());
        BigInteger[] cycleLabels = {BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO};
        assertThrows(IllegalStateException.class, () -> ShortestPathResult.create(graph, 0, ShortestPathStatus.CONCLUIDO,
                cycleLabels, new int[] {-1, 2, 1}, new int[] {1, 2, 3}, metrics, null));
        assertThrows(IllegalArgumentException.class, () -> ShortestPathResult.create(graph, 0, ShortestPathStatus.CANCELADO,
                null, null, null, metrics, null));
    }

    @Test
    void secondaryTimingFailurePreservesPrimaryCancellationAndReportsAbsentTime() {
        var calls = new AtomicInteger();
        var token = new ShortestPathCancellationToken(); token.cancel();
        var solver = new DijkstraShortestPathSolver(() -> {
            if (calls.incrementAndGet() == 3) throw new IllegalStateException("final clock failure");
            return 0;
        }, null);
        var result = solver.solve(ShortestPathTestGraphs.chain(), ShortestPathExecutionOptions.demonstration(), token);
        assertEquals(ShortestPathStatus.CANCELADO, result.status());
        assertTrue(result.metrics().elapsedNanos().isEmpty());
        assertTrue(result.reason().orElseThrow().contains("final clock failure"));
    }

    @Test
    void metricCountersCannotMisrepresentRelaxations() {
        assertThrows(IllegalArgumentException.class, () -> new ShortestPathMetrics(OptionalLong.of(-1), 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShortestPathMetrics(OptionalLong.empty(), 0, 0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShortestPathMetrics(OptionalLong.empty(), 0, 1, 0, 1, 0));
    }
}
