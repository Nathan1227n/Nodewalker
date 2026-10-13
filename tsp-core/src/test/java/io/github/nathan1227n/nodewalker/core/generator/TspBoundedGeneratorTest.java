package io.github.nathan1227n.nodewalker.core.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TspBoundedGeneratorTest {
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 2})
    void rejectsInvalidCityCounts(int n) {
        GenerationOptions options = new GenerationOptions(5, 4096);

        assertThrows(IllegalArgumentException.class, () -> TspGenerator.generate(n, 0L, options));
        assertThrows(IllegalArgumentException.class, () -> TspGenerator.estimatePeakMemoryBytes(n));
    }

    @Test
    void rejectsNullOptions() {
        assertThrows(NullPointerException.class, () -> TspGenerator.generate(3, 0L, null));
    }

    @ParameterizedTest
    @MethodSource("sizesAndSeeds")
    void sufficientExplicitBudgetsPreserveEveryCityAndCostOfTheOriginalApi(int n, long seed) {
        TspProblem original = TspGenerator.generate(n, seed);
        GenerationOptions options = new GenerationOptions(10000, TspGenerator.estimatePeakMemoryBytes(n).longValueExact());
        TspProblem controlled = TspGenerator.generate(n, seed, options);

        assertSameInstanceData(original, controlled);
        assertFalse(options.cancellationToken().isCancellationRequested());
    }

    private static Stream<Arguments> sizesAndSeeds() {
        return IntStream.of(3, 4, 10, 100).boxed().flatMap(n -> LongStream
                .of(0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 657).mapToObj(seed -> Arguments.of(n, seed)));
    }

    @Test
    void fiveCandidatesReproduceTheExplicitReferenceAfterTheRejectedThirdTrio() {
        TspProblem problem = TspGenerator.generate(4, 657L, new GenerationOptions(5, 2304));
        List<City> expected = List.of(new City(0, 930, 591, 463), new City(1, 188, 89, 36),
                new City(2, 284, 169, 46), new City(3, 232, 587, 882));
        long[][] costs = {{0, 992, 877, 814}, {992, 0, 125, 983}, {877, 125, 0, 936}, {814, 983, 936, 0}};

        assertEquals(expected, problem.cities());
        assertEquals(657L, problem.seed().orElseThrow());
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(costs[i][j], problem.cost(i, j));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"3, 3, 2", "4, 4, 3"})
    void rejectedCandidatesConsumeBudgetAndNoExtraTrioIsDrawn(int n, long budget, int accepted) {
        GenerationLimitExceededException error = assertThrows(GenerationLimitExceededException.class,
                () -> TspGenerator.generate(n, 657L, new GenerationOptions(budget, 4096)));

        assertEquals(budget, error.maxCandidates());
        assertEquals(budget, error.attemptedCandidates());
        assertEquals(accepted, error.acceptedCities());
        assertTrue(error.getMessage().contains("insuficiente"));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void candidateBudgetBelowCityCountFailsBeforeEnteringGeneration(long budget) {
        AtomicBoolean enteredGeneration = new AtomicBoolean();
        GenerationLimitExceededException error = assertThrows(GenerationLimitExceededException.class,
                () -> TspGenerator.generate(3, 0L, new GenerationOptions(budget, 4096), (phase, attempts, accepted) -> {
                    if (phase != TspGenerator.Phase.PREFLIGHT) {
                        enteredGeneration.set(true);
                    }
                }));

        assertEquals(0L, error.attemptedCandidates());
        assertEquals(0, error.acceptedCities());
        assertFalse(enteredGeneration.get());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 1935})
    void insufficientMemoryFailsBeforeEnteringGeneration(long budget) {
        AtomicBoolean enteredGeneration = new AtomicBoolean();
        GenerationMemoryLimitExceededException error = assertThrows(GenerationMemoryLimitExceededException.class,
                () -> TspGenerator.generate(3, 0L, new GenerationOptions(3, budget), (phase, attempts, accepted) -> {
                    if (phase != TspGenerator.Phase.PREFLIGHT) {
                        enteredGeneration.set(true);
                    }
                }));

        assertEquals(BigInteger.valueOf(1936), error.estimatedBytes());
        assertEquals(budget, error.maxEstimatedBytes());
        assertFalse(enteredGeneration.get());
    }

    @Test
    void exactMemoryAndCandidateBudgetsAreInclusiveForSuccessfulGeneration() {
        TspProblem controlled = TspGenerator.generate(3, 0L, new GenerationOptions(3, 1936));

        assertSameInstanceData(TspGenerator.generate(3, 0L), controlled);
    }

    @ParameterizedTest
    @CsvSource({"3, 1936", "4, 2304", "100, 186624"})
    void memoryEstimateMatchesIndependentLiteralReferenceValues(int n, long expected) {
        assertEquals(BigInteger.valueOf(expected), TspGenerator.estimatePeakMemoryBytes(n));
    }

    @Test
    void largestIntegerSizeHasAnExactEstimateAboveLongInsteadOfWrapping() {
        assertEquals(new BigInteger("73786976775874544400"), TspGenerator.estimatePeakMemoryBytes(Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @ValueSource(ints = {100000, Integer.MAX_VALUE})
    void excessiveRequestsAreRejectedWithoutAllocatingCityOrMatrixArrays(int n) {
        long budget = n == Integer.MAX_VALUE ? Long.MAX_VALUE : 4096L;
        AtomicBoolean enteredGeneration = new AtomicBoolean();

        GenerationMemoryLimitExceededException error = assertThrows(GenerationMemoryLimitExceededException.class,
                () -> TspGenerator.generate(n, 0L, new GenerationOptions(Long.MAX_VALUE, budget),
                        (phase, attempts, accepted) -> {
                            if (phase != TspGenerator.Phase.PREFLIGHT) {
                                enteredGeneration.set(true);
                            }
                        }));

        assertTrue(error.estimatedBytes().compareTo(BigInteger.valueOf(budget)) > 0);
        assertFalse(enteredGeneration.get());
    }

    @Test
    void reuseOfOptionsAndInterveningCallsNeverSharesAttemptCountersOrRandomState() {
        GenerationOptions options = new GenerationOptions(5, 4096);
        TspProblem first = TspGenerator.generate(4, 657L, options);
        TspGenerator.generate(3, 0L, options);
        TspGenerator.generate(10, -1L, new GenerationOptions(100, 20000));

        assertSameInstanceData(first, TspGenerator.generate(4, 657L, options));
    }

    @Test
    void originalApiStillIgnoresThreadInterruptionAndPreservesItsRandomReference() {
        boolean previouslyInterrupted = Thread.currentThread().isInterrupted();
        try {
            Thread.currentThread().interrupt();
            TspProblem problem = TspGenerator.generate(3, 0L);

            assertEquals(List.of(new City(0, 187, 226, 998), new City(1, 632, 39, 35),
                    new City(2, 618, 743, 783)), problem.cities());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            if (!previouslyInterrupted) {
                Thread.interrupted();
            }
        }
    }

    private static void assertSameInstanceData(TspProblem expected, TspProblem actual) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected.originIndex(), actual.originIndex());
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.cities(), actual.cities());
        for (int i = 0; i < expected.size(); i++) {
            for (int j = 0; j < expected.size(); j++) {
                assertEquals(expected.cost(i, j), actual.cost(i, j));
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> actual.cities().clear());
    }
}
