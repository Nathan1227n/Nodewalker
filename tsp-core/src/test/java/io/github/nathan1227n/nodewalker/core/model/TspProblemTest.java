package io.github.nathan1227n.nodewalker.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TspProblemTest {
    private static final List<City> CITIES = List.of(
            new City(0, 120, 300, 50),
            new City(1, 250, 100, 75),
            new City(2, 400, 200, 100));

    @Test
    void acceptsThreeCitiesAndFixesOriginAtZero() {
        long[][] matrix = triangleCosts();
        TspProblem problem = new TspProblem(CITIES, matrix);

        assertEquals(3, problem.size());
        assertEquals(0, problem.originIndex());
        assertEquals(CITIES, problem.cities());
        assertCostsEqual(matrix, problem);
    }

    @Test
    void manualInstanceHasNoGenerationSeed() {
        assertTrue(new TspProblem(CITIES, triangleCosts()).seed().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void preservesGenerationSeedWithoutChangingCitiesOrCosts(long seed) {
        TspProblem problem = new TspProblem(CITIES, triangleCosts(), seed);

        assertEquals(seed, problem.seed().orElseThrow());
        assertEquals(CITIES, problem.cities());
        assertCostsEqual(triangleCosts(), problem);
    }

    @Test
    void seededConstructorPreservesDefensiveCopiesAndValidation() {
        List<City> cities = new ArrayList<>(CITIES);
        long[][] matrix = triangleCosts();
        TspProblem problem = new TspProblem(cities, matrix, 657L);

        cities.clear();
        matrix[0][1] = -1;
        assertEquals(CITIES, problem.cities());
        assertCostsEqual(triangleCosts(), problem);
        assertThrows(UnsupportedOperationException.class, () -> problem.cities().clear());
        assertThrows(IllegalArgumentException.class, () -> new TspProblem(CITIES, matrix, 657L));
        assertThrows(NullPointerException.class, () -> new TspProblem(null, triangleCosts(), 657L));
        assertThrows(IllegalArgumentException.class, () -> new TspProblem(List.of(), new long[0][0], 657L));
    }

    @Test
    void acceptsFourCityReferenceMatrixIndependentlyOfGeometry() {
        List<City> cities = new ArrayList<>(CITIES);
        cities.add(new City(3, 0, 0, 0));
        long[][] matrix = {
                {0, 80, 100, 90},
                {80, 0, 70, 110},
                {100, 70, 0, 60},
                {90, 110, 60, 0}
        };

        TspProblem problem = new TspProblem(cities, matrix);

        assertEquals(4, problem.size());
        assertEquals(0, problem.originIndex());
        assertCostsEqual(matrix, problem);
    }

    @Test
    void preservesListOrder() {
        TspProblem problem = new TspProblem(new ArrayList<>(CITIES), triangleCosts());

        assertEquals(CITIES, problem.cities());
    }

    @Test
    void acceptsZeroCostEdgesAndCoincidentCoordinates() {
        List<City> cities = List.of(new City(0, 0, 0, 0), new City(1, 0, 0, 0), new City(2, 0, 0, 0));
        long[][] matrix = new long[3][3];

        assertCostsEqual(matrix, new TspProblem(cities, matrix));
    }

    @Test
    void acceptsLongMaximumEdgesButDetectsOverflowWhenTheyAreAdded() {
        long[][] matrix = {
                {0, Long.MAX_VALUE, 1},
                {Long.MAX_VALUE, 0, 1},
                {1, 1, 0}
        };
        TspProblem problem = new TspProblem(CITIES, matrix);

        assertEquals(Long.MAX_VALUE, problem.cost(0, 1));
        assertEquals(Long.MAX_VALUE, problem.cost(1, 0));
        assertThrows(ArithmeticException.class,
                () -> CostMath.addExact(problem.cost(0, 1), problem.cost(1, 2)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsFewerThanThreeCities(int count) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspProblem(CITIES.subList(0, count), new long[count][count]));

        assertTrue(error.getMessage().contains("N >= 3"));
    }

    @ParameterizedTest
    @MethodSource("invalidCityOrders")
    void rejectsDuplicateMissingOrOutOfOrderCityIndices(List<City> cities) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspProblem(cities, triangleCosts()));

        assertTrue(error.getMessage().contains("índice da cidade na posição"));
    }

    private static Stream<List<City>> invalidCityOrders() {
        return Stream.of(
                List.of(CITIES.get(1), CITIES.get(0), CITIES.get(2)),
                List.of(CITIES.get(0), CITIES.get(1), CITIES.get(1)),
                List.of(CITIES.get(0), CITIES.get(1), new City(3, 0, 0, 0)),
                List.of(CITIES.get(0), CITIES.get(1), new City(Integer.MAX_VALUE, 0, 0, 0)));
    }

    @Test
    void rejectsNullCityList() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new TspProblem(null, triangleCosts()));

        assertTrue(error.getMessage().contains("lista de cidades"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsNullCityAtAnyPosition(int index) {
        List<City> cities = new ArrayList<>(CITIES);
        cities.set(index, null);

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new TspProblem(cities, triangleCosts()));

        assertTrue(error.getMessage().contains("posição " + index));
    }

    @Test
    void rejectsNullMatrix() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new TspProblem(CITIES, null));

        assertTrue(error.getMessage().contains("matriz de custos"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 4})
    void rejectsWrongMatrixRowCount(int count) {
        assertThrows(IllegalArgumentException.class, () -> new TspProblem(CITIES, new long[count][3]));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 4})
    void rejectsRaggedOrRectangularMatrix(int rowLength) {
        long[][] matrix = triangleCosts();
        matrix[2] = new long[rowLength];

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspProblem(CITIES, matrix));

        assertTrue(error.getMessage().contains("linha 2"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsNullMatrixRow(int index) {
        long[][] matrix = triangleCosts();
        matrix[index] = null;

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new TspProblem(CITIES, matrix));

        assertTrue(error.getMessage().contains("linha " + index));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeCost(long cost) {
        long[][] matrix = triangleCosts();
        matrix[0][1] = cost;
        matrix[1][0] = cost;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspProblem(CITIES, matrix));

        assertTrue(error.getMessage().contains("não negativo"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsNonzeroDiagonal(int index) {
        long[][] matrix = triangleCosts();
        matrix[index][index] = 1;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspProblem(CITIES, matrix));

        assertTrue(error.getMessage().contains("diagonal"));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 0", "0, 2", "2, 0", "1, 2", "2, 1"})
    void rejectsAsymmetryInEitherTriangle(int from, int to) {
        long[][] matrix = triangleCosts();
        matrix[from][to]++;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspProblem(CITIES, matrix));

        assertTrue(error.getMessage().contains("simétrica"));
    }

    @Test
    void copiesInputMatrixRows() {
        long[][] matrix = triangleCosts();
        TspProblem problem = new TspProblem(CITIES, matrix);

        matrix[0][1] = -1;
        matrix[1][0] = -1;
        matrix[2][2] = 99;

        assertCostsEqual(triangleCosts(), problem);
    }

    @Test
    void copiesInputMatrixOuterArray() {
        long[][] matrix = triangleCosts();
        TspProblem problem = new TspProblem(CITIES, matrix);

        matrix[0] = null;
        matrix[1] = new long[0];

        assertCostsEqual(triangleCosts(), problem);
    }

    @Test
    void copiesInputCityList() {
        List<City> cities = new ArrayList<>(CITIES);
        TspProblem problem = new TspProblem(cities, triangleCosts());

        cities.set(0, new City(0, -10, -20, -30));
        cities.clear();

        assertEquals(3, problem.size());
        assertEquals(CITIES, problem.cities());
        assertEquals(0, problem.originIndex());
    }

    @Test
    void exposesAnUnmodifiableCityList() {
        TspProblem problem = new TspProblem(CITIES, triangleCosts());

        assertThrows(UnsupportedOperationException.class, () -> problem.cities().set(0, new City(0, 0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> problem.cities().add(new City(3, 0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> problem.cities().clear());
        assertEquals(CITIES, problem.cities());
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "0, -1", "3, 0", "0, 3", "2147483647, 0", "0, -2147483648"})
    void rejectsOutOfBoundsCostIndices(int from, int to) {
        TspProblem problem = new TspProblem(CITIES, triangleCosts());

        assertThrows(IndexOutOfBoundsException.class, () -> problem.cost(from, to));
    }

    private static long[][] triangleCosts() {
        return new long[][] {
                {0, 10, 20},
                {10, 0, 15},
                {20, 15, 0}
        };
    }

    private static void assertCostsEqual(long[][] expected, TspProblem actual) {
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected.length; j++) {
                assertEquals(expected[i][j], actual.cost(i, j), "Custo (" + i + ", " + j + ")");
            }
        }
    }
}
