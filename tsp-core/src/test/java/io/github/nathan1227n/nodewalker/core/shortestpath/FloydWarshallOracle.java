package io.github.nathan1227n.nodewalker.core.shortestpath;

import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import java.math.BigInteger;

/** Test-only all-pairs DP. Does not call Dijkstra, its adjacency traversal, heap, result or arithmetic helpers. */
final class FloydWarshallOracle {
    static BigInteger[][] distances(RefugeGraph graph) {
        int n = graph.size();
        if (n > 12) throw new IllegalArgumentException("Test oracle requires N <= 12.");
        BigInteger[][] d = new BigInteger[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) d[i][j] = BigInteger.ZERO;
                else if (graph.topology().hasEdge(i, j)) d[i][j] = BigInteger.valueOf(graph.problem().cost(i, j));
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (d[i][k] == null || d[k][j] == null) continue;
                    BigInteger candidate = d[i][k].add(d[k][j]);
                    if (d[i][j] == null || candidate.compareTo(d[i][j]) < 0) d[i][j] = candidate;
                }
            }
        }
        return d;
    }
}
