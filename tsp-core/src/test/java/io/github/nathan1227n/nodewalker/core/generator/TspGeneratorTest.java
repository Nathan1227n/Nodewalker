package io.github.nathan1227n.nodewalker.core.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TspGeneratorTest {
    private static final City ORIGIN = new City(0, 0, 0, 0);

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 2})
    void rejectsFewerThanThreeCities(int n) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TspGenerator.generate(n, 0L));

        assertTrue(error.getMessage().contains("N >= 3"));
    }

    @Test
    void acceptsThreeCitiesAndFixesOriginAndSequentialIndices() {
        TspProblem problem = TspGenerator.generate(3, 0L);

        assertEquals(3, problem.size());
        assertEquals(0, problem.originIndex());
        for (int i = 0; i < problem.size(); i++) {
            assertEquals(i, problem.cities().get(i).index());
        }
    }

    @ParameterizedTest
    @MethodSource("sizesAndSeeds")
    void sameSizeAndSeedProduceIdenticalCitiesAndCosts(int n, long seed) {
        TspProblem first = TspGenerator.generate(n, seed);
        TspProblem second = TspGenerator.generate(n, seed);

        assertEquals(seed, first.seed().orElseThrow());
        assertEquals(first.seed(), second.seed());
        assertEquals(first.cities(), second.cities());
        assertSameCosts(first, second);
    }

    @ParameterizedTest
    @MethodSource("sizesAndSeeds")
    void generatedGeometryAndMatrixRespectContract(int n, long seed) {
        TspProblem problem = TspGenerator.generate(n, seed);

        assertEquals(n, problem.size());
        assertEquals(0, problem.originIndex());
        for (int i = 0; i < n; i++) {
            City city = problem.cities().get(i);
            assertEquals(i, city.index());
            assertTrue(city.x() >= 0 && city.x() <= 1000);
            assertTrue(city.y() >= 0 && city.y() <= 1000);
            assertTrue(city.z() >= 0 && city.z() <= 1000);
            assertEquals(0L, problem.cost(i, i));
            for (int j = i + 1; j < n; j++) {
                BigInteger squared = exactSquaredDistance(city, problem.cities().get(j));
                assertTrue(squared.compareTo(BigInteger.valueOf(2500)) >= 0);
                assertTrue(problem.cost(i, j) >= 0L);
                assertEquals(problem.cost(i, j), problem.cost(j, i));
                assertEquals(exactRoundedSquareRoot(squared), problem.cost(i, j));
            }
        }
    }

    private static Stream<Arguments> sizesAndSeeds() {
        return IntStream.of(3, 4, 10, 100).boxed().flatMap(n -> seeds()
                .mapToObj(seed -> Arguments.of(n, seed)));
    }

    private static LongStream seeds() {
        return LongStream.of(0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 657);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 657})
    void increasingSizePreservesTheAcceptedPrefixAndItsCosts(long seed) {
        TspProblem small = TspGenerator.generate(3, seed);
        TspProblem larger = TspGenerator.generate(10, seed);

        assertEquals(small.cities(), larger.cities().subList(0, 3));
        for (int i = 0; i < small.size(); i++) {
            for (int j = 0; j < small.size(); j++) {
                assertEquals(small.cost(i, j), larger.cost(i, j));
            }
        }
    }

    @Test
    void interveningGenerationsDoNotChangeRepeatedResults() {
        TspProblem first = TspGenerator.generate(4, 657L);
        TspGenerator.generate(100, 1L);
        TspGenerator.generate(3, Long.MIN_VALUE);
        TspGenerator.generate(10, -1L);
        TspProblem repeated = TspGenerator.generate(4, 657L);

        assertEquals(first.cities(), repeated.cities());
        assertSameCosts(first, repeated);
    }

    @Test
    void coordinateRangeIncludesBothEndpoints() {
        assertEquals(0, TspGenerator.generate(3, 39L).cities().get(2).x());
        assertEquals(1000, TspGenerator.generate(3, 12L).cities().get(0).x());
    }

    // Referências calculadas previamente em Python: LCG de 48 bits do Random
    // e raízes Decimal com 50 dígitos. Não são derivadas da implementação sob teste.
    @Test
    void matchesExplicitReferenceForThreeCitiesAndSeedZero() {
        List<City> expected = List.of(
                new City(0, 187, 226, 998),
                new City(1, 632, 39, 35),
                new City(2, 618, 743, 783));
        long[][] costs = {
                {0, 1077, 707},
                {1077, 0, 1027},
                {707, 1027, 0}
        };

        TspProblem problem = TspGenerator.generate(3, 0L);

        assertEquals(expected, problem.cities());
        assertCostsEqual(costs, problem);
    }

    @Test
    void consumesAllThreeDrawsOfRejectedCandidateAndContinuesTheReferenceSequence() {
        List<City> expected = List.of(
                new City(0, 930, 591, 463),
                new City(1, 188, 89, 36),
                new City(2, 284, 169, 46),
                new City(3, 232, 587, 882));
        City rejected = new City(2, 936, 637, 445);
        long[][] costs = {
                {0, 992, 877, 814},
                {992, 0, 125, 983},
                {877, 125, 0, 936},
                {814, 983, 936, 0}
        };
        // Apenas a sequência bruta do Random: nenhum algoritmo de aceitação é replicado.
        Random raw = new Random(657L);
        for (City attempt : List.of(expected.get(0), expected.get(1), rejected,
                expected.get(2), expected.get(3))) {
            assertEquals(attempt.x(), raw.nextInt(0, 1001));
            assertEquals(attempt.y(), raw.nextInt(0, 1001));
            assertEquals(attempt.z(), raw.nextInt(0, 1001));
        }
        assertEquals(BigInteger.valueOf(2476), exactSquaredDistance(expected.get(0), rejected));
        // sqrt(2476) < 50, embora round(sqrt(2476)) = 50.
        assertEquals(50L, exactRoundedSquareRoot(BigInteger.valueOf(2476)));

        TspProblem problem = TspGenerator.generate(4, 657L);

        assertFalse(problem.cities().contains(rejected));
        assertEquals(expected, problem.cities());
        assertCostsEqual(costs, problem);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, false", "49, 0, 0, false", "50, 0, 0, true", "51, 0, 0, true",
            "0, 49, 0, false", "0, 50, 0, true", "0, 51, 0, true",
            "0, 0, 49, false", "0, 0, 50, true", "0, 0, 51, true",
            "30, 40, 0, true", "30, 0, 40, true", "30, 39, 8, false"
    })
    void minimumSeparationUsesUnroundedThreeDimensionalDistance(int x, int y, int z, boolean accepted) {
        assertEquals(accepted, TspGenerator.isFarEnough(new City(1, x, y, z), List.of(ORIGIN)));
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 201})
    void checksDistanceToEveryPreviouslyAcceptedCity(int x) {
        List<City> existing = List.of(ORIGIN, new City(1, 100, 0, 0), new City(2, 200, 0, 0));

        assertFalse(TspGenerator.isFarEnough(new City(3, x, 0, 0), existing));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, 0", "3, 4, 0, 5", "1, 1, 1, 2", "1, 1, 0, 1",
            "30, 0, 40, 50", "0, 0, 50, 50", "9, 3, 0, 9", "9, 3, 1, 10",
            "1000, 1000, 1000, 1732"
    })
    void roundsKnownDistancesInThreeDimensions(int x, int y, int z, long expectedCost) {
        City other = new City(1, x, y, z);

        assertEquals(expectedCost, TspGenerator.roundedDistance(ORIGIN, other));
        assertEquals(expectedCost, TspGenerator.roundedDistance(other, ORIGIN));
    }

    @Test
    void generatedCitiesRemainImmutable() {
        TspProblem problem = TspGenerator.generate(3, 0L);

        assertThrows(UnsupportedOperationException.class, () -> problem.cities().set(0, ORIGIN));
    }

    private static void assertSameCosts(TspProblem first, TspProblem second) {
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            for (int j = 0; j < first.size(); j++) {
                assertEquals(first.cost(i, j), second.cost(i, j));
            }
        }
    }

    private static void assertCostsEqual(long[][] expected, TspProblem problem) {
        assertEquals(expected.length, problem.size());
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected.length; j++) {
                assertEquals(expected[i][j], problem.cost(i, j));
            }
        }
    }

    private static BigInteger exactSquaredDistance(City a, City b) {
        BigInteger dx = BigInteger.valueOf(b.x()).subtract(BigInteger.valueOf(a.x()));
        BigInteger dy = BigInteger.valueOf(b.y()).subtract(BigInteger.valueOf(a.y()));
        BigInteger dz = BigInteger.valueOf(b.z()).subtract(BigInteger.valueOf(a.z()));
        return dx.pow(2).add(dy.pow(2)).add(dz.pow(2));
    }

    // Oráculo inteiro: compara a distância com floor(sqrt(s)) + 0,5 sem ponto flutuante.
    private static long exactRoundedSquareRoot(BigInteger squared) {
        BigInteger floor = squared.sqrt();
        BigInteger twiceMidpoint = floor.shiftLeft(1).add(BigInteger.ONE);
        boolean roundUp = squared.shiftLeft(2).compareTo(twiceMidpoint.pow(2)) >= 0;
        return (roundUp ? floor.add(BigInteger.ONE) : floor).longValueExact();
    }
}
