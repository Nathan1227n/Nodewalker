package io.github.nathan1227n.nodewalker.core;

import java.math.BigInteger;

/**
 * Espaço teórico da força bruta com origem fixa (índice 0).
 * Aritmética arbitrária impede overflow ao exibir o preflight.
 */
public final class SearchSpace {
    private SearchSpace() {
    }

    public static BigInteger completeRoutes(int cityCount) {
        if (cityCount < 3) {
            throw new IllegalArgumentException("Uma instância TSP deve ter N >= 3.");
        }

        BigInteger routes = BigInteger.ONE;
        for (int factor = 2; factor < cityCount; factor++) {
            routes = routes.multiply(BigInteger.valueOf(factor));
        }
        return routes;
    }
}
