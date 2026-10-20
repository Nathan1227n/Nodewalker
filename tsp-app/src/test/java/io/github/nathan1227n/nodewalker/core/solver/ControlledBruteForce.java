package io.github.nathan1227n.nodewalker.core.solver;

import java.util.concurrent.*;

/** Test-only access to the existing package-private audit seam. No production core changes. */
public final class ControlledBruteForce {
    private ControlledBruteForce() { }
    public static BruteForceSolver afterIncumbent(CountDownLatch entered, CountDownLatch release) {
        return new BruteForceSolver(() -> 0L, (phase, nodes, routes) -> {
            if (routes == 1) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
    }
}
