package io.github.nathan1227n.nodewalker.core.shortestpath;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ShortestPathResultValidationTest {
    @ParameterizedTest(name = "previous={0}, cyclic={1}")
    @MethodSource("invalidSnapshots")
    void realValidationFailureAlwaysBecomesErrorAndPublishesNoPaths(ShortestPathStatus previous, boolean cyclic) {
        var graph = ShortestPathTestGraphs.chain();
        BigInteger[] labels = {BigInteger.ZERO, BigInteger.valueOf(2), BigInteger.valueOf(cyclic ? 5 : 6)};
        int[] parents = cyclic ? new int[] {-1, 2, 1} : new int[] {-1, 0, 1};
        int[] rank = {1, 2, 3};
        var metrics = new ShortestPathMetrics(OptionalLong.of(123), 3, 4, 2, 2, 1);
        String reason = previous == ShortestPathStatus.CONCLUIDO ? null : "Original terminal: " + previous;

        // Exercise the real validator, not a mock factory that merely throws an arbitrary exception.
        assertThrows(IllegalStateException.class,
                () -> ShortestPathResult.create(graph, 0, previous, labels, parents, rank, metrics, reason));
        var result = DijkstraShortestPathSolver.finishResult(graph, 0, previous, labels, parents, rank, metrics, reason);

        assertEquals(ShortestPathStatus.ERRO, result.status());
        assertFalse(result.allDistancesResolved());
        assertSame(metrics, result.metrics());
        String diagnostic = result.reason().orElseThrow();
        assertTrue(diagnostic.contains("Result validation failure"));
        assertTrue(diagnostic.contains("previous status=" + previous));
        assertTrue(diagnostic.contains("java.lang.IllegalStateException: Invalid predecessor/distance."));
        if (reason != null) assertTrue(diagnostic.contains(reason));
        for (int vertex = 0; vertex < graph.size(); vertex++) {
            assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(vertex));
            assertTrue(result.distanceTo(vertex).isEmpty());
            assertTrue(result.pathTo(vertex).isEmpty());
            assertTrue(result.predecessorOf(vertex).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ShortestPathStatus.class, names = {"CANCELADO", "LIMITE_ATINGIDO"})
    void validInterruptionKeepsItsStatusAndOnlyConfirmedDistances(ShortestPathStatus status) {
        var graph = ShortestPathTestGraphs.chain();
        BigInteger[] labels = {BigInteger.ZERO, BigInteger.valueOf(2), BigInteger.valueOf(5)};
        int[] parents = {-1, 0, 1};
        int[] rank = {1, 2, 0}; // vertex 2 has a tentative label, not a confirmed shortest path
        var metrics = new ShortestPathMetrics(OptionalLong.of(123), 2, 3, 2, 2, 1);
        String reason = "Valid interruption: " + status;
        var result = DijkstraShortestPathSolver.finishResult(graph, 0, status, labels, parents, rank, metrics, reason);

        assertEquals(status, result.status());
        assertEquals(reason, result.reason().orElseThrow());
        assertFalse(result.allDistancesResolved());
        assertEquals(BigInteger.ZERO, result.distanceTo(0).orElseThrow());
        assertEquals(BigInteger.valueOf(2), result.distanceTo(1).orElseThrow());
        assertEquals(List.of(0, 1), result.pathTo(1).orElseThrow());
        assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO, result.vertexState(2));
        assertTrue(result.distanceTo(2).isEmpty());
        assertTrue(result.pathTo(2).isEmpty());
        assertTrue(result.predecessorOf(2).isEmpty());
    }

    static Stream<Arguments> invalidSnapshots() {
        return Arrays.stream(ShortestPathStatus.values())
                .flatMap(status -> Stream.of(false, true).map(cyclic -> Arguments.of(status, cyclic)));
    }
}
