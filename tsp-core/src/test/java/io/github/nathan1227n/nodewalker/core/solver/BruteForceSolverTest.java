package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.SearchSpace;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BruteForceSolverTest {
    private final TspSolver solver = new BruteForceSolver();

    @ParameterizedTest
    @CsvSource({"3, 2", "4, 6", "5, 24"})
    void evaluatesEveryPermutationIncludingReverseOrientations(int n, long expectedRoutes) {
        TspProblem problem = equalEdges(n, 7);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, expectedRoutes);
        assertEquals(firstRoute(n), result.route().orElseThrow().indices());
        assertEquals(7L * n, result.cost().orElseThrow());
    }

    @Test
    void solvesManualTriangleIncludingTheReturnEdge() {
        TspProblem problem = ContractTestProblems.triangle();

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 2);
        assertEquals(List.of(0, 1, 2, 0), result.route().orElseThrow().indices());
        assertEquals(45L, result.cost().orElseThrow());
    }

    @Test
    void solvesDoc03Section7WithTheFirstLexicographicOptimum() {
        TspProblem problem = ContractTestProblems.fourCityReference();

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 6);
        assertEquals(300L, result.cost().orElseThrow());
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
    }

    @Test
    void replacesAnEarlierMoreExpensiveIncumbent() {
        TspProblem problem = problem(new long[][] {
                {0, 9, 1, 1},
                {9, 0, 1, 1},
                {1, 1, 0, 9},
                {1, 1, 9, 0}
        });

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 6);
        assertEquals(4L, result.cost().orElseThrow());
        assertEquals(List.of(0, 2, 1, 3, 0), result.route().orElseThrow().indices());
    }

    @Test
    void findsAKnownFiveCityOptimumAfterTheFirstPermutation() {
        // Só as arestas do ciclo 0-2-4-1-3-0 custam 1; as demais custam 9.
        // Todo ciclo tem cinco arestas >= 1, e esse ciclo atinge o limite inferior 5.
        TspProblem problem = problem(new long[][] {
                {0, 9, 1, 1, 9},
                {9, 0, 9, 1, 1},
                {1, 9, 0, 9, 1},
                {1, 1, 9, 0, 9},
                {9, 1, 1, 9, 0}
        });

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 24);
        assertEquals(5L, result.cost().orElseThrow());
        assertEquals(List.of(0, 2, 4, 1, 3, 0), result.route().orElseThrow().indices());
    }

    @ParameterizedTest
    @CsvSource({"3, 0", "4, 0", "5, 0", "3, 7", "4, 7", "5, 7"})
    void keepsTheFirstRouteOnTotalTiesWithoutStoppingAtZero(int n, long edgeCost) {
        TspProblem problem = equalEdges(n, edgeCost);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, SearchSpace.completeRoutes(n).longValueExact());
        assertEquals(firstRoute(n), result.route().orElseThrow().indices());
        assertEquals(n * edgeCost, result.cost().orElseThrow());
    }

    @Test
    void keepsTheFirstOptimumBetweenDistinctTiedCyclesBeforeCanonicalizing() {
        TspProblem problem = problem(new long[][] {
                {0, 5, 1, 5},
                {5, 0, 5, 1},
                {1, 5, 0, 5},
                {5, 1, 5, 0}
        });

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 6);
        assertEquals(12L, result.cost().orElseThrow());
        // Substituir em empate escolheria 0-3-1-2-0; sua normalização produziria
        // 0-2-1-3-0, outro ciclo ótimo, em vez da primeira ótima 0-1-3-2-0.
        assertEquals(List.of(0, 1, 3, 2, 0), result.route().orElseThrow().indices());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void acceptsALongMaximumOptimumWithoutAnInfinitySentinel(int maximumEdge) {
        TspProblem problem = ContractTestProblems.triangle(
                maximumEdge == 0 ? Long.MAX_VALUE : 0,
                maximumEdge == 1 ? Long.MAX_VALUE : 0,
                maximumEdge == 2 ? Long.MAX_VALUE : 0);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 2);
        assertEquals(Long.MAX_VALUE, result.cost().orElseThrow());
        assertEquals(List.of(0, 1, 2, 0), result.route().orElseThrow().indices());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsOverflowBeforeAnySuccessfulEvaluation(boolean onReturn) {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, onReturn ? 0 : 1, onReturn ? 1 : 0);

        TspResult result = solver.solve(problem);

        assertOverflow(problem, result, 0);
        assertTrue(result.route().isEmpty());
        assertTrue(result.cost().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsLaterOverflowEvenWhenARepresentableOptimumExists(boolean onReturn) {
        long edge02 = onReturn ? Long.MAX_VALUE : 1;
        long edge13 = onReturn ? 1 : Long.MAX_VALUE;
        TspProblem problem = problem(new long[][] {
                {0, 1, edge02, 1},
                {1, 0, 1, edge13},
                {edge02, 1, 0, 1},
                {1, edge13, 1, 0}
        });

        TspResult result = solver.solve(problem);

        assertOverflow(problem, result, 1);
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        assertEquals(4L, result.cost().orElseThrow());
    }

    @Test
    void preservesALongMaximumIncumbentWhenALaterRouteOverflows() {
        TspProblem problem = problem(new long[][] {
                {0, Long.MAX_VALUE, 0, 0},
                {Long.MAX_VALUE, 0, 0, 1},
                {0, 0, 0, 0},
                {0, 1, 0, 0}
        });

        TspResult result = solver.solve(problem);

        assertOverflow(problem, result, 1);
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        assertEquals(Long.MAX_VALUE, result.cost().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("generatedInstances")
    void repeatsTheSameResultAcrossInterleavedRunsAndSolverInstances(int n, long seed) {
        TspProblem problem = TspGenerator.generate(n, seed);
        TspResult first = solver.solve(problem);

        solver.solve(ContractTestProblems.fourCityReference());
        TspResult repeated = solver.solve(problem);
        TspResult independentSolver = new BruteForceSolver().solve(problem);

        long expectedRoutes = SearchSpace.completeRoutes(n).longValueExact();
        assertCompleted(problem, first, expectedRoutes);
        assertCompleted(problem, repeated, expectedRoutes);
        assertCompleted(problem, independentSolver, expectedRoutes);
        assertEquals(first.route(), repeated.route());
        assertEquals(first.cost(), repeated.cost());
        assertEquals(first.metrics(), repeated.metrics());
        assertEquals(first.route(), independentSolver.route());
        assertEquals(first.cost(), independentSolver.cost());
        assertEquals(first.metrics(), independentSolver.metrics());
    }

    @Test
    void solvesTheKnownGeneratedReferenceWithLiteralRouteAndCost() {
        TspProblem problem = TspGenerator.generate(4, 657L);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result, 6);
        // Matriz de referência do gerador: 877 + 125 + 983 + 814 = 2799.
        assertEquals(2799L, result.cost().orElseThrow());
        assertEquals(List.of(0, 2, 1, 3, 0), result.route().orElseThrow().indices());
    }

    @Test
    void preservesImmutableResultsAfterSubsequentCalls() {
        TspResult result = solver.solve(ContractTestProblems.fourCityReference());

        solver.solve(ContractTestProblems.triangle());

        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        assertEquals(300L, result.cost().orElseThrow());
        assertMetrics(result.metrics(), 6);
        assertThrows(UnsupportedOperationException.class, () -> result.route().orElseThrow().indices().set(1, 3));
    }

    @Test
    void rejectsNullInputAccordingToTheSolverContract() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> solver.solve(null));

        assertTrue(error.getMessage().contains("problema"));
    }

    private static void assertCompleted(TspProblem problem, TspResult result, long expectedRoutes) {
        assertEquals(SolverStatus.CONCLUIDO, result.status());
        assertTrue(result.optimalityGuaranteed());
        assertTrue(result.reason().isEmpty());
        TspRoute route = result.route().orElseThrow();
        assertEquals(problem.size(), route.cityCount());
        assertEquals(problem.size() + 1, route.indices().size());
        assertEquals(0, route.indices().getFirst());
        assertEquals(0, route.indices().getLast());
        assertEquals(IntStream.range(0, problem.size()).boxed().collect(Collectors.toSet()),
                route.indices().stream().collect(Collectors.toSet()));
        assertTrue(route.isCanonical());
        assertEquals(RouteValidator.validateAndCost(problem, route), result.cost().orElseThrow());
        assertMetrics(result.metrics(), expectedRoutes);
        assertEquals(SearchSpace.completeRoutes(problem.size()), BigInteger.valueOf(expectedRoutes));
    }

    private static void assertOverflow(TspProblem problem, TspResult result, long expectedRoutes) {
        assertEquals(SolverStatus.ERRO, result.status());
        assertFalse(result.optimalityGuaranteed());
        assertTrue(result.reason().orElseThrow().contains("ArithmeticException"));
        assertTrue(result.reason().orElseThrow().contains("Long.MAX_VALUE"));
        assertMetrics(result.metrics(), expectedRoutes);
        assertEquals(result.route().isPresent(), result.cost().isPresent());
        result.route().ifPresent(route ->
                assertEquals(RouteValidator.validateAndCost(problem, route), result.cost().orElseThrow()));
    }

    private static void assertMetrics(SolverMetrics metrics, long expectedRoutes) {
        assertTrue(metrics.completeRoutesEvaluated().isPresent());
        assertEquals(expectedRoutes, metrics.completeRoutesEvaluated().orElseThrow());
        assertTrue(metrics.elapsedNanos().isEmpty());
        assertTrue(metrics.visitedNodes().isEmpty());
        assertTrue(metrics.discardedRoutes().isEmpty());
    }

    private static List<Integer> firstRoute(int n) {
        List<Integer> indices = new ArrayList<>(IntStream.range(0, n).boxed().toList());
        indices.add(0);
        return indices;
    }

    private static TspProblem equalEdges(int n, long cost) {
        long[][] costs = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                costs[i][j] = i == j ? 0 : cost;
            }
        }
        return problem(costs);
    }

    private static TspProblem problem(long[][] costs) {
        List<City> cities = IntStream.range(0, costs.length)
                .mapToObj(i -> new City(i, 0, 0, 0)).toList();
        return new TspProblem(cities, costs);
    }

    private static Stream<Arguments> generatedInstances() {
        return IntStream.of(3, 4, 5, 6).boxed().flatMap(n ->
                LongStream.of(0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 657)
                        .mapToObj(seed -> Arguments.of(n, seed)));
    }
}
