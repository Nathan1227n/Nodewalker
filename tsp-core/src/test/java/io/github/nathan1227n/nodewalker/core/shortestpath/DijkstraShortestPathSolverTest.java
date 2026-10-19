package io.github.nathan1227n.nodewalker.core.shortestpath;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Edge;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopologyGenerator;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DijkstraShortestPathSolverTest {
    @Test
    void manualIntermediatePathHasDistanceFiveAndExactMetrics() {
        var graph = ShortestPathTestGraphs.chain();
        var r = new DijkstraShortestPathSolver().solve(graph);
        assertEquals(ShortestPathStatus.CONCLUIDO, r.status());
        assertTrue(r.allDistancesResolved());
        assertTrue(r.reason().isEmpty());
        assertEquals(BigInteger.valueOf(5), r.distanceTo(2).orElseThrow());
        assertEquals(List.of(0, 1, 2), r.pathTo(2).orElseThrow());
        assertEquals(List.of(0), r.pathTo(0).orElseThrow());
        assertTrue(r.predecessorOf(0).isEmpty());
        assertEquals(1, r.predecessorOf(2).orElseThrow());
        assertEquals(3, r.metrics().processedVertices());
        assertEquals(4, r.metrics().examinedEdges());
        assertEquals(2, r.metrics().relaxationAttempts());
        assertEquals(2, r.metrics().successfulRelaxations());
        assertEquals(1, r.metrics().peakQueueSize());
        assertEquals(BigInteger.valueOf(5), FloydWarshallOracle.distances(graph)[0][2]);
        audit(graph, r, FloydWarshallOracle.distances(graph)[0]);
    }

    @Test
    void completeManualNonMetricMatrixCanPreferAnIntermediateVertex() {
        var graph = new RefugeGraph(ShortestPathTestGraphs.chain().problem(), RefugeTopology.complete(3));
        var r = new DijkstraShortestPathSolver().solve(graph);
        assertEquals(List.of(0, 1, 2), r.pathTo(2).orElseThrow());
        assertEquals(BigInteger.valueOf(5), r.distanceTo(2).orElseThrow());
        assertEquals(6, r.metrics().examinedEdges());
        assertEquals(3, r.metrics().successfulRelaxations());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, Long.MAX_VALUE})
    void acceptsZeroAndMaximumLongWeightsAndDistances(long weight) {
        var graph = new RefugeGraph(ContractTestProblems.equalEdges(4, weight), RefugeTopology.complete(4));
        var r = new DijkstraShortestPathSolver().solve(graph);
        for (int v = 1; v < 4; v++) {
            assertEquals(BigInteger.valueOf(weight), r.distanceTo(v).orElseThrow());
            assertEquals(List.of(0, v), r.pathTo(v).orElseThrow());
        }
        // With MAX weights, examined alternative sums are 2*MAX, not wrapped or saturated.
        audit(graph, r, FloydWarshallOracle.distances(graph)[0]);
    }

    @Test
    void sparseDistancesBeyondLongRemainFiniteAndPublishable() {
        var graph = ShortestPathTestGraphs.manual(new long[][] {
                {0, Long.MAX_VALUE, 0}, {Long.MAX_VALUE, 0, 1}, {0, 1, 0}}, new Edge(0, 1), new Edge(1, 2));
        var r = new DijkstraShortestPathSolver().solve(graph);
        var beyond = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(beyond, r.distanceTo(2).orElseThrow());
        assertEquals(List.of(0, 1, 2), r.pathTo(2).orElseThrow());
        assertEquals(beyond, FloydWarshallOracle.distances(graph)[0][2]);
        assertEquals(ShortestPathResult.VertexState.CONFIRMADO, r.vertexState(2));
        audit(graph, r, FloydWarshallOracle.distances(graph)[0]);
    }

    @Test
    void equalPathsKeepFirstPredecessorDespiteReorderedManualEdges() {
        var problem = ContractTestProblems.equalEdges(4, 1);
        var edges = List.of(new Edge(0, 1), new Edge(0, 2), new Edge(1, 3), new Edge(2, 3));
        var first = new DijkstraShortestPathSolver().solve(new RefugeGraph(problem, RefugeTopology.manual(4, edges)));
        assertEquals(List.of(0, 1, 3), first.pathTo(3).orElseThrow());
        for (int seed = 0; seed < 20; seed++) {
            var shuffled = new ArrayList<>(edges);
            Collections.shuffle(shuffled, new Random(seed));
            var r = new DijkstraShortestPathSolver().solve(new RefugeGraph(problem, RefugeTopology.manual(4, shuffled)));
            for (int v = 0; v < 4; v++) {
                assertEquals(first.pathTo(v), r.pathTo(v));
                assertEquals(first.distanceTo(v), r.distanceTo(v));
            }
            assertEquals(first.metrics().examinedEdges(), r.metrics().examinedEdges());
            assertEquals(first.metrics().successfulRelaxations(), r.metrics().successfulRelaxations());
        }
    }

    @Test
    void deterministicTiePolicyDoesNotPromiseLexicographicallySmallestPath() {
        var problem = ContractTestProblems.problem(new long[][] {
                {0, 1, 2, 100}, {1, 0, 1, 100}, {2, 1, 0, 1}, {100, 100, 1, 0}});
        var r = new DijkstraShortestPathSolver().solve(new RefugeGraph(problem, RefugeTopology.complete(4)));
        // [0,1,2,3] has the same cost 3 and is lexicographically smaller; equality keeps parent 0 of vertex 2.
        assertEquals(List.of(0, 2, 3), r.pathTo(3).orElseThrow());
        assertEquals(BigInteger.valueOf(3), r.distanceTo(3).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void zeroWeightCyclesProduceAcyclicPredecessorsFromEveryOrigin(int source) {
        var graph = new RefugeGraph(ContractTestProblems.equalEdges(5, 0), RefugeTopology.complete(5));
        var r = new DijkstraShortestPathSolver().solve(graph, source, generous(), new ShortestPathCancellationToken());
        audit(graph, r, FloydWarshallOracle.distances(graph)[source]);
        assertEquals(List.of(source), r.pathTo(source).orElseThrow());
    }

    @Test
    void completedDisconnectedGraphDistinguishesUnreachableFromZero() {
        var graph = ShortestPathTestGraphs.manual(new long[4][4], new Edge(0, 1), new Edge(2, 3));
        var r = new DijkstraShortestPathSolver().solve(graph);
        assertEquals(BigInteger.ZERO, r.distanceTo(1).orElseThrow());
        assertEquals(ShortestPathResult.VertexState.INALCANCAVEL, r.vertexState(2));
        assertTrue(r.distanceTo(2).isEmpty());
        assertTrue(r.pathTo(2).isEmpty());
        assertTrue(r.predecessorOf(2).isEmpty());
        assertNull(FloydWarshallOracle.distances(graph)[0][2]);
    }

    @ParameterizedTest(name = "N={0}, seed={1}, topology={2}")
    @MethodSource("randomGraphs")
    void auditsEverySourceAgainstIndependentAllPairsDp(int n, long seed, int mode) {
        Random random = new Random(seed);
        long[][] matrix = new long[n][n];
        var edges = new ArrayList<Edge>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                long cost = random.nextInt(7) == 0 ? Long.MAX_VALUE : random.nextInt(8);
                matrix[i][j] = matrix[j][i] = cost;
                if (random.nextBoolean()) edges.add(new Edge(i, j));
            }
        }
        var topology = mode == 0 ? RefugeTopology.complete(n) : mode == 1
                ? RefugeTopologyGenerator.connectedSparse(n, seed, n / 2)
                : RefugeTopology.manual(n, edges);
        var graph = new RefugeGraph(ContractTestProblems.problem(matrix), topology);
        var reference = FloydWarshallOracle.distances(graph);
        var solver = new DijkstraShortestPathSolver();
        for (int source = 0; source < n; source++) {
            var r = solver.solve(graph, source, generous(), new ShortestPathCancellationToken());
            audit(graph, r, reference[source]);
        }
        // No geometry-derived replacement, normalization, or mutation of original matrix costs.
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) assertEquals(matrix[i][j], graph.problem().cost(i, j));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, 657, Long.MIN_VALUE, Long.MAX_VALUE})
    void reproducibleEuclideanInstancesWorkWithCompleteAndSparseTopologies(long seed) {
        var problem = TspGenerator.generate(8, seed);
        var originalCities = List.copyOf(problem.cities());
        for (var topology : List.of(RefugeTopology.complete(8), RefugeTopologyGenerator.connectedSparse(8, seed, 3))) {
            var graph = new RefugeGraph(problem, topology);
            var r = new DijkstraShortestPathSolver().solve(graph);
            var again = new DijkstraShortestPathSolver().solve(graph);
            audit(graph, r, FloydWarshallOracle.distances(graph)[0]);
            for (int v = 0; v < 8; v++) assertEquals(r.pathTo(v), again.pathTo(v));
            assertSame(problem, r.graph().problem());
            assertEquals(originalCities, problem.cities());
        }
    }

    @Test
    void resultPathsAreImmutableAndDestinationIndicesAreChecked() {
        var r = new DijkstraShortestPathSolver().solve(ShortestPathTestGraphs.chain());
        assertThrows(UnsupportedOperationException.class, () -> r.pathTo(2).orElseThrow().set(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> r.distanceTo(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> r.pathTo(3));
    }

    @Test
    void oracleRejectsSizeAboveItsTestLimitBeforeAllocation() {
        var graph = new RefugeGraph(ContractTestProblems.equalEdges(13, 1), RefugeTopology.complete(13));
        assertThrows(IllegalArgumentException.class, () -> FloydWarshallOracle.distances(graph));
    }

    static ShortestPathExecutionOptions generous() {
        return ShortestPathExecutionOptions.limits(10_000, 1_000_000, 60_000);
    }

    static Stream<Arguments> randomGraphs() {
        return IntStream.rangeClosed(3, 7).boxed().flatMap(n -> IntStream.range(0, 20).boxed()
                .flatMap(seed -> IntStream.range(0, 3).mapToObj(mode -> Arguments.of(n, (long) seed, mode))));
    }

    static void audit(RefugeGraph graph, ShortestPathResult r, BigInteger[] expected) {
        assertEquals(ShortestPathStatus.CONCLUIDO, r.status(), () -> r.reason().toString());
        assertTrue(r.metrics().elapsedNanos().orElseThrow() >= 0);
        assertTrue(r.metrics().peakQueueSize() <= graph.size());
        for (int v = 0; v < graph.size(); v++) {
            assertEquals(expected[v], r.distanceTo(v).orElse(null), "source=" + r.origin() + ", target=" + v);
            if (expected[v] == null) {
                assertEquals(ShortestPathResult.VertexState.INALCANCAVEL, r.vertexState(v));
                assertTrue(r.pathTo(v).isEmpty());
                continue;
            }
            var path = r.pathTo(v).orElseThrow();
            assertEquals(r.origin(), path.getFirst());
            assertEquals(v, path.getLast());
            assertEquals(path.size(), new HashSet<>(path).size());
            BigInteger sum = BigInteger.ZERO;
            for (int step = 1; step < path.size(); step++) {
                int from = path.get(step - 1), to = path.get(step);
                assertTrue(graph.topology().hasEdge(from, to));
                assertEquals(from, r.predecessorOf(to).orElseThrow());
                sum = sum.add(BigInteger.valueOf(graph.problem().cost(from, to)));
            }
            assertEquals(expected[v], sum);
        }
    }
}
