package io.github.nathan1227n.nodewalker.cli;

import java.math.BigDecimal;
import java.util.Arrays;

/** Mediana exata em ns, incluindo meio nanossegundo, sem somar longs nem usar double. */
final class Median {
    private Median() {
    }

    static BigDecimal of(long[] observations) {
        if (observations.length == 0) {
            throw new IllegalArgumentException("A mediana exige pelo menos uma medição.");
        }
        long[] sorted = observations.clone();
        for (long duration : sorted) {
            if (duration < 0) {
                throw new IllegalArgumentException("Durações não podem ser negativas.");
            }
        }
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        BigDecimal upper = BigDecimal.valueOf(sorted[middle]);
        return sorted.length % 2 == 1 ? upper
                : BigDecimal.valueOf(sorted[middle - 1]).add(upper).divide(BigDecimal.valueOf(2));
    }
}
