package io.github.nathan1227n.nodewalker.core.shortestpath;

import java.util.concurrent.*;

/** Test-only observer seam already audited in core; pause after two real definitive fixations. */
public final class ControlledDijkstra {
    private ControlledDijkstra() { }
    public static DijkstraShortestPathSolver afterTwoVertices(CountDownLatch entered, CountDownLatch release) {
        return new DijkstraShortestPathSolver(() -> 0L, (phase, vertices, edges) -> {
            if (phase == DijkstraShortestPathSolver.Phase.AFTER_FIXATION && vertices == 2) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
    }
}
