package io.github.nathan1227n.nodewalker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PreflightTest {
    @ParameterizedTest
    @CsvSource({"3, 2, 16", "4, 6, 48", "5, 24, 192", "10, 362880, 2903040"})
    void computesExactFactorialAndAllCalls(int n, long routes, long total) {
        Preflight preflight = Preflight.check(options(n, 3, 5, 10_000_000, 100_000, 67_108_864));

        assertEquals(BigInteger.valueOf(routes), preflight.routes());
        assertEquals(BigInteger.valueOf(total), preflight.totalRoutes());
        assertTrue(preflight.estimatedBytes().signum() > 0);
    }

    @Test
    void acceptsInclusiveRouteAndGenerationBudgets() {
        Preflight preflight = Preflight.check(options(4, 3, 5, 48, 4, 2304));

        assertEquals(BigInteger.valueOf(2304), preflight.estimatedBytes());
        assertEquals(BigInteger.valueOf(48), preflight.totalRoutes());
    }

    @Test
    void rejectsTotalWorkIncludingWarmupEvenThoughEachRunFits() {
        String message = rejected(options(4, 3, 5, 47, 4, 2304));

        assertTrue(message.contains("(N-1)! = 6"));
        assertTrue(message.contains("bateria = 48"));
        assertTrue(message.contains("--max-total-routes=47"));
    }

    @Test
    void rejectsElevenCitiesWithTheDefaultWholeBatchBudget() {
        assertTrue(rejected(options(11, 3, 5, 10_000_000, 100_000, 67_108_864)).contains("29030400"));
    }

    @Test
    void detectsGenerationBudgetsBeforeAttemptingTheFactorial() {
        assertTrue(rejected(options(4, 0, 1, 6, 3, 2304)).contains("--max-candidates"));
        assertTrue(rejected(options(4, 0, 1, 6, 4, 2303)).contains("2304 bytes"));
        assertTrue(rejected(options(Integer.MAX_VALUE, 0, 1, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
                .contains("Memória"));
    }

    @Test
    void computesTotalsBeyondLongWithoutOverflow() {
        String message = rejected(options(21, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Long.MAX_VALUE, 100_000, 67_108_864));
        BigInteger expected = new BigInteger("2432902008176640000").multiply(BigInteger.valueOf(4_294_967_294L));

        assertTrue(message.contains(expected.toString()), message);
    }

    @Test
    void printsTheExactTwentyOneFactorialBeyondLongRange() {
        String message = rejected(options(22, 0, 1, Long.MAX_VALUE, 100_000, 67_108_864));

        assertTrue(message.contains("(N-1)! = 51090942171709440000"));
    }

    @ParameterizedTest
    @ValueSource(ints = {23, 25, 1_000_000})
    void rejectsLargeSizesUsingAnExplicitLowerBoundRatherThanBuildingHugeFactorials(int n) {
        String message = rejected(options(n, 0, 1, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));

        assertTrue(message.contains("(N-1)! >= 51090942171709440000"), message);
    }

    private static String rejected(CliOptions options) {
        return assertThrows(IllegalArgumentException.class, () -> Preflight.check(options)).getMessage();
    }

    private static CliOptions options(int n, int warmup, int runs, long routes, long candidates, long bytes) {
        return new CliOptions(n, 0, "brute-force", warmup, runs, routes, candidates, bytes);
    }
}
