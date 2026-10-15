package io.github.nathan1227n.nodewalker.core.solver;

import java.math.BigInteger;

/**
 * Contagem privada por execução: depth inclui a origem, e cada poda elimina (N-depth)! folhas.
 * O caminho quente incrementa somente long; a precisão arbitrária fica na consolidação.
 */
final class PruningHistogram {
    private final long[] counts;

    PruningHistogram(int cityCount) {
        if (cityCount < 3) {
            throw new IllegalArgumentException("O histograma exige N >= 3.");
        }
        counts = new long[cityCount];
    }

    /** Copia contadores iniciais, permitindo verificar overflow sem uma busca impraticável. */
    PruningHistogram(long[] initialCounts) {
        if (initialCounts.length < 3) {
            throw new IllegalArgumentException("O histograma exige N >= 3.");
        }
        counts = initialCounts.clone();
        for (long count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("Contagem de podas negativa.");
            }
        }
    }

    void record(int depth) {
        if (depth < 1 || depth > counts.length) {
            throw new IllegalArgumentException("Profundidade de poda inválida: " + depth);
        }
        counts[depth - 1] = Math.incrementExact(counts[depth - 1]);
    }

    BigInteger discardedRoutes() {
        int firstPrunedIndex = 0;
        while (firstPrunedIndex < counts.length && counts[firstPrunedIndex] == 0) {
            firstPrunedIndex++;
        }
        BigInteger factorial = BigInteger.ONE;
        BigInteger total = BigInteger.ZERO;
        for (int index = counts.length - 1; index >= firstPrunedIndex; index--) {
            int remaining = counts.length - index - 1;
            if (remaining > 1) {
                factorial = factorial.multiply(BigInteger.valueOf(remaining));
            }
            if (counts[index] != 0) {
                total = total.add(factorial.multiply(BigInteger.valueOf(counts[index])));
            }
        }
        return total;
    }
}
