package io.github.nathan1227n.nodewalker.core.shortestpath;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Edge;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class FloydWarshallOracleTest {
    @Test
    void independentOracleMatchesLiteralAllPairsReferenceWithIsolatedVertex() {
        var graph = ShortestPathTestGraphs.manual(new long[][] {
                {0, 2, 99, 7}, {2, 0, 3, 8}, {99, 3, 0, 9}, {7, 8, 9, 0}}, new Edge(0, 1), new Edge(1, 2));
        Long[][] expected = {{0L, 2L, 5L, null}, {2L, 0L, 3L, null}, {5L, 3L, 0L, null}, {null, null, null, 0L}};
        var actual = FloydWarshallOracle.distances(graph);
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            assertEquals(expected[i][j] == null ? null : BigInteger.valueOf(expected[i][j]), actual[i][j]);
        }
    }
}
