package io.github.nathan1227n.nodewalker.core.graph;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.generator.GenerationCancellationToken;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefugeTopologyTest {
    @Test
    void completeTopologyIsImplicitSortedAndHasNoSelfConnections() {
        var topology = RefugeTopology.complete(5);
        assertEquals(10, topology.edgeCount());
        assertEquals(RefugeTopology.Kind.COMPLETE, topology.kind());
        for (int i = 0; i < 5; i++) {
            assertEquals(4, topology.degree(i));
            int previous = -1;
            for (int p = 0; p < topology.degree(i); p++) {
                int v = topology.neighborAt(i, p);
                assertTrue(v > previous);
                assertNotEquals(i, v);
                assertTrue(topology.hasEdge(i, v));
                previous = v;
            }
            assertFalse(topology.hasEdge(i, i));
        }
        // Metadata alone does not allocate adjacency or an N² matrix.
        assertEquals(2_305_843_005_992_468_481L, RefugeTopology.complete(Integer.MAX_VALUE).edgeCount());
    }

    @Test
    void manualTopologyCopiesInputNormalizesOrientationAndSortsNeighbors() {
        var edges = new ArrayList<>(List.of(new RefugeTopology.Edge(3, 0), new RefugeTopology.Edge(2, 0)));
        var topology = RefugeTopology.manual(4, edges);
        edges.clear();
        assertEquals(2, topology.edgeCount());
        assertEquals(2, topology.neighborAt(0, 0));
        assertEquals(3, topology.neighborAt(0, 1));
        assertTrue(topology.hasEdge(3, 0));
        assertEquals(0, topology.degree(1));
        assertFalse(topology.hasEdge(1, 2));
        assertTrue(topology.seed().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, 657, Long.MIN_VALUE, Long.MAX_VALUE})
    void seededChainAndChordsAreConnectedReproducibleAndSparse(long seed) {
        for (int n : new int[] {3, 4, 8, 50}) {
            for (int extras : new int[] {0, n - 2}) {
                var first = RefugeTopologyGenerator.connectedSparse(n, seed, extras);
                var again = RefugeTopologyGenerator.connectedSparse(n, seed, extras);
                assertEquals(n - 1L + extras, first.edgeCount());
                assertEquals(seed, first.seed().orElseThrow());
                assertEquals(RefugeTopology.Kind.SPARSE_CONNECTED, first.kind());
                boolean[] seen = new boolean[n];
                var queue = new ArrayDeque<Integer>();
                seen[0] = true;
                queue.add(0);
                int reached = 0;
                while (!queue.isEmpty()) {
                    int current = queue.remove();
                    reached++;
                    for (int p = 0; p < first.degree(current); p++) {
                        int next = first.neighborAt(current, p);
                        assertTrue(first.hasEdge(next, current));
                        if (!seen[next]) { seen[next] = true; queue.add(next); }
                    }
                }
                assertEquals(n, reached);
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) assertEquals(first.hasEdge(i, j), again.hasEdge(i, j));
                }
            }
        }
    }

    @Test
    void resourceRefusalPrecedesLargeAllocationAndGenerationCancellationIsSeparate() {
        assertThrows(IllegalArgumentException.class,
                () -> RefugeTopology.manual(Integer.MAX_VALUE, List.of(), 1024));
        assertThrows(IllegalArgumentException.class,
                () -> RefugeTopologyGenerator.connectedSparse(Integer.MAX_VALUE, 0, 0));
        var token = new GenerationCancellationToken();
        token.cancel();
        assertThrows(CancellationException.class, () -> RefugeTopologyGenerator.connectedSparse(8, 0, 2,
                RefugeTopology.DEFAULT_MAX_ESTIMATED_BYTES, token));
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> RefugeTopologyGenerator.connectedSparse(8, 0, 2));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test
    void rejectsInvalidEdgesIndicesSizesAndBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new RefugeTopology.Edge(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RefugeTopology.Edge(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> RefugeTopology.complete(2));
        assertThrows(IllegalArgumentException.class, () -> RefugeTopology.manual(3,
                List.of(new RefugeTopology.Edge(0, 1), new RefugeTopology.Edge(1, 0))));
        assertThrows(IndexOutOfBoundsException.class,
                () -> RefugeTopology.manual(3, List.of(new RefugeTopology.Edge(0, 3))));
        assertThrows(NullPointerException.class, () -> RefugeTopology.manual(3, null));
        assertThrows(IllegalArgumentException.class, () -> RefugeTopology.manual(3, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> RefugeTopologyGenerator.connectedSparse(3, 0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> RefugeTopology.complete(3).neighborAt(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> RefugeTopology.complete(3).hasEdge(-1, 0));
    }

    @Test
    void graphRetainsOriginalWeightsIncludingZeroAndLongMaximum() {
        var problem = ContractTestProblems.triangle(Long.MAX_VALUE, 0, 1);
        var topology = RefugeTopology.manual(3, List.of(new RefugeTopology.Edge(0, 1), new RefugeTopology.Edge(1, 2)));
        var graph = new RefugeGraph(problem, topology);
        assertSame(problem, graph.problem());
        assertEquals(Long.MAX_VALUE, graph.cost(0, 1));
        assertEquals(0, graph.cost(1, 2));
        assertEquals(1, problem.cost(0, 2));
        assertThrows(IllegalArgumentException.class, () -> graph.cost(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new RefugeGraph(problem, RefugeTopology.complete(4)));
        assertThrows(NullPointerException.class, () -> new RefugeGraph(null, topology));
    }
}
