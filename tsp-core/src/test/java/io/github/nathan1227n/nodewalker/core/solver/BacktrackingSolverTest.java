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

class BacktrackingSolverTest {
    private final TspSolver solver = new BacktrackingSolver();

    @Test
    void solvesTheManualTriangleIncludingItsReturnEdge() {
        TspProblem problem = ContractTestProblems.triangle();

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(List.of(0, 1, 2, 0), result.route().orElseThrow().indices());
        assertEquals(45L, result.cost().orElseThrow());
        assertMetrics(result, 2, 5);
    }

    @Test
    void solvesDoc03Section7WithTheExpectedFirstOptimum() {
        TspProblem problem = ContractTestProblems.fourCityReference();

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
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

        assertCompleted(problem, result);
        assertEquals(4L, result.cost().orElseThrow());
        assertEquals(List.of(0, 2, 1, 3, 0), result.route().orElseThrow().indices());
    }

    @Test
    void findsTheKnownFiveCityOptimumAfterTheFirstPermutation() {
        // Todo ciclo tem cinco arestas >= 1. Só 0-2-4-1-3-0 e seu inverso atingem 5.
        TspProblem problem = problem(new long[][] {
                {0, 9, 1, 1, 9},
                {9, 0, 9, 1, 1},
                {1, 9, 0, 9, 1},
                {1, 1, 9, 0, 9},
                {9, 1, 1, 9, 0}
        });

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(5L, result.cost().orElseThrow());
        assertEquals(List.of(0, 2, 4, 1, 3, 0), result.route().orElseThrow().indices());
    }

    @ParameterizedTest
    @CsvSource({"3, 0, 1, 4", "4, 0, 1, 7", "5, 0, 1, 11",
            "3, 7, 2, 5", "4, 7, 6, 16", "5, 7, 24, 65"})
    void preservesTheFirstRouteOnTotalTiesAndReportsObservedWork(int n, long edgeCost,
                                                               long expectedRoutes, long expectedNodes) {
        TspProblem problem = equalEdges(n, edgeCost);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(firstRoute(n), result.route().orElseThrow().indices());
        assertEquals(n * edgeCost, result.cost().orElseThrow());
        // Pesos positivos: nenhuma soma parcial alcança N * edgeCost, portanto
        // visitam-se todos os prefixos. Com zero, a primeira ótima permite podar em igualdade.
        assertMetrics(result, expectedRoutes, expectedNodes);
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

        assertCompleted(problem, result);
        assertEquals(12L, result.cost().orElseThrow());
        // Trocar por custo igual levaria a outro ciclo, mesmo após canonical().
        assertEquals(List.of(0, 1, 3, 2, 0), result.route().orElseThrow().indices());
    }

    @Test
    void prunesAnEqualCostLeafBeforeEvaluatingTheReturnEdge() {
        TspProblem problem = ContractTestProblems.triangle(0, 5, 0);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(5L, result.cost().orElseThrow());
        // Os cinco estados são 0, 01, 012, 02 e 021. A folha 021 é podada;
        // somente 0120 tem o custo completo efetivamente calculado.
        assertMetrics(result, 1, 5);
        assertEquals(BigInteger.ONE, result.metrics().discardedRoutes().orElseThrow());
    }

    @Test
    void prunesStrictlyMoreExpensivePartialStatesWithoutVisitingTheirDescendants() {
        TspProblem problem = problem(new long[][] {
                {0, 1, 100, 1},
                {1, 0, 1, 100},
                {100, 1, 0, 1},
                {1, 100, 1, 0}
        });

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(4L, result.cost().orElseThrow());
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        // Completos: 01230 e 03210. Prefixos 013, 02 e 031 são podados.
        assertMetrics(result, 2, 10);
        assertEquals(BigInteger.valueOf(4), result.metrics().discardedRoutes().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"3, 1", "4, 5", "5, 23", "6, 119", "7, 719", "8, 5039", "9, 40319",
            "10, 362879", "11, 3628799", "12, 39916799"})
    void accountsForAllLeavesOnZeroCostTiesIncludingTsp08(int n, long discarded) {
        TspProblem problem = equalEdges(n, 0);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(firstRoute(n), result.route().orElseThrow().indices());
        assertEquals(0, result.cost().orElseThrow());
        assertMetrics(result, 1, 1L + n * (n - 1L) / 2);
        assertEquals(BigInteger.valueOf(discarded), result.metrics().discardedRoutes().orElseThrow());
    }

    @Test
    void accountsForTwentyFourFactorialLeavesByPruningOnlyThreeHundredAndOneStates() {
        // Confirma a poda em uma instância pequena antes do cenário que seria impraticável sem ela.
        assertMetrics(solver.solve(equalEdges(5, 0)), 1, 11);
        TspProblem problem = equalEdges(25, 0);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(firstRoute(25), result.route().orElseThrow().indices());
        assertEquals(0, result.cost().orElseThrow());
        assertMetrics(result, 1, 301);
        BigInteger discarded = result.metrics().discardedRoutes().orElseThrow();
        assertEquals(new BigInteger("620448401733239439359999"), discarded);
        assertTrue(discarded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertEquals(new BigInteger("620448401733239439360000"), discarded.add(BigInteger.ONE));
        assertEquals(new BigInteger("620448401733239439360000"), SearchSpace.completeRoutes(25));
    }

    @Test
    void preservesEarlierPrunedLeavesWhenALaterBranchOverflowsWithoutCountingTheFailureAsPruning() {
        long[][] costs = new long[5][5];
        for (int i = 0; i < costs.length; i++) {
            for (int j = 0; j < costs.length; j++) {
                costs[i][j] = i == j ? 0 : 1;
            }
        }
        costs[2][4] = costs[4][2] = 100;
        costs[1][3] = costs[3][1] = Long.MAX_VALUE;
        TspProblem problem = problem(costs);

        TspResult result = solver.solve(problem);

        // 012340 custa 5; 0124 é podado (1! folha); somar 01 + 13 depois causa overflow.
        assertOverflow(problem, result, 1, 6);
        assertEquals(5, result.cost().orElseThrow());
        assertEquals(firstRoute(5), result.route().orElseThrow().indices());
        assertEquals(BigInteger.ONE, result.metrics().discardedRoutes().orElseThrow());
        BigInteger covered = BigInteger.valueOf(result.metrics().completeRoutesEvaluated().orElseThrow())
                .add(result.metrics().discardedRoutes().orElseThrow());
        assertTrue(covered.compareTo(SearchSpace.completeRoutes(5)) < 0);
    }

    @ParameterizedTest
    @CsvSource({"0, 2, 5", "1, 1, 5", "2, 1, 4"})
    void acceptsALongMaximumOptimumWithoutUsingAnInfinitySentinel(int maximumEdge,
                                                               long expectedRoutes, long expectedNodes) {
        TspProblem problem = ContractTestProblems.triangle(
                maximumEdge == 0 ? Long.MAX_VALUE : 0,
                maximumEdge == 1 ? Long.MAX_VALUE : 0,
                maximumEdge == 2 ? Long.MAX_VALUE : 0);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(Long.MAX_VALUE, result.cost().orElseThrow());
        assertEquals(List.of(0, 1, 2, 0), result.route().orElseThrow().indices());
        assertMetrics(result, expectedRoutes, expectedNodes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsOverflowBeforeTheFirstIncumbentInPartialCostOrReturn(boolean onReturn) {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, onReturn ? 0 : 1, onReturn ? 1 : 0);

        TspResult result = solver.solve(problem);

        assertOverflow(problem, result, 0, onReturn ? 3 : 2);
        assertTrue(result.route().isEmpty());
        assertTrue(result.cost().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsLaterOverflowAndPreservesOnlyThePreviousValidIncumbent(boolean onReturn) {
        long edge02 = onReturn ? Long.MAX_VALUE : 1;
        long edge13 = onReturn ? 1 : Long.MAX_VALUE;
        TspProblem problem = problem(new long[][] {
                {0, 1, edge02, 1},
                {1, 0, 1, edge13},
                {edge02, 1, 0, 1},
                {1, edge13, 1, 0}
        });

        TspResult result = solver.solve(problem);

        assertOverflow(problem, result, 1, onReturn ? 6 : 4);
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        assertEquals(4L, result.cost().orElseThrow());
    }

    @Test
    void preservesALongMaximumIncumbentWithoutIgnoringALaterDetectedOverflow() {
        TspProblem problem = problem(new long[][] {
                {0, Long.MAX_VALUE, 0, 0},
                {Long.MAX_VALUE, 0, 0, 1},
                {0, 0, 0, 0},
                {0, 1, 0, 0}
        });

        TspResult result = solver.solve(problem);

        assertOverflow(problem, result, 1, 4);
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        assertEquals(Long.MAX_VALUE, result.cost().orElseThrow());
    }

    @Test
    void doesNotEvaluateAnOverflowingReturnBehindAnAlreadyPrunedLeaf() {
        // Toda aresta >= 1, e o primeiro ciclo custa 4: o ótimo é conhecido.
        TspProblem problem = problem(new long[][] {
                {0, 1, Long.MAX_VALUE, 1},
                {1, 0, 1, 2},
                {Long.MAX_VALUE, 1, 0, 1},
                {1, 2, 1, 0}
        });

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(4L, result.cost().orElseThrow());
        assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices());
        // 0132 e 0312 chegam à folha com custo parcial 4 e são podadas antes
        // do retorno Long.MAX_VALUE. Já 02 é podado como estado parcial.
        assertMetrics(result, 2, 12);
        // A força bruta tenta todos os retornos; aqui ela detecta overflow.
        assertEquals(SolverStatus.ERRO, new BruteForceSolver().solve(problem).status());
    }

    @ParameterizedTest(name = "N={0}, seed={1}")
    @MethodSource("differentialCases")
    void agreesWithBruteForceOnSmallRepresentableInstances(int n, long seed) {
        TspProblem problem = TspGenerator.generate(n, seed);

        TspResult reference = new BruteForceSolver().solve(problem);
        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(SolverStatus.CONCLUIDO, reference.status());
        assertEquals(reference.cost(), result.cost());
        assertEquals(reference.route(), result.route());
        assertTrue(result.metrics().completeRoutesEvaluated().orElseThrow()
                <= reference.metrics().completeRoutesEvaluated().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"3, 0", "4, 657", "5, -1", "6, -9223372036854775808", "7, 9223372036854775807"})
    void repeatsIdenticalResultsAndDeterministicCountersAcrossInterleavedCallsAndNewSolvers(int n, long seed) {
        TspProblem problem = TspGenerator.generate(n, seed);
        TspResult first = solver.solve(problem);

        solver.solve(ContractTestProblems.fourCityReference());
        TspResult repeated = solver.solve(problem);
        TspResult independent = new BacktrackingSolver().solve(problem);

        assertCompleted(problem, first);
        assertCompleted(problem, repeated);
        assertCompleted(problem, independent);
        assertEquals(first.route(), repeated.route());
        assertEquals(first.cost(), repeated.cost());
        assertDeterministicMetrics(first.metrics(), repeated.metrics());
        assertEquals(first.route(), independent.route());
        assertEquals(first.cost(), independent.cost());
        assertDeterministicMetrics(first.metrics(), independent.metrics());
    }

    @Test
    void solvesTheKnownGeneratedReferenceWithLiteralCostAndRoute() {
        TspProblem problem = TspGenerator.generate(4, 657L);

        TspResult result = solver.solve(problem);

        assertCompleted(problem, result);
        assertEquals(2799L, result.cost().orElseThrow());
        assertEquals(List.of(0, 2, 1, 3, 0), result.route().orElseThrow().indices());
    }

    @Test
    void preservesImmutableResultsWhenSubsequentSearchesChangeSizeAndIncumbent() {
        TspResult first = solver.solve(ContractTestProblems.fourCityReference());
        SolverMetrics metrics = first.metrics();

        solver.solve(ContractTestProblems.triangle(0, 0, 0));

        assertEquals(List.of(0, 1, 2, 3, 0), first.route().orElseThrow().indices());
        assertEquals(300L, first.cost().orElseThrow());
        assertEquals(metrics, first.metrics());
        assertThrows(UnsupportedOperationException.class, () -> first.route().orElseThrow().indices().set(1, 3));
    }

    @Test
    void rejectsNullAccordingToTheSolverContract() {
        NullPointerException failure = assertThrows(NullPointerException.class, () -> solver.solve(null));

        assertTrue(failure.getMessage().contains("problema"));
    }

    private static void assertCompleted(TspProblem problem, TspResult result) {
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
        long evaluated = result.metrics().completeRoutesEvaluated().orElseThrow();
        assertTrue(evaluated >= 1);
        assertTrue(BigInteger.valueOf(evaluated).compareTo(SearchSpace.completeRoutes(problem.size())) <= 0);
        assertTrue(result.metrics().visitedNodes().orElseThrow() >= problem.size());
        assertTrue(result.metrics().elapsedNanos().orElseThrow() >= 0);
        BigInteger discarded = result.metrics().discardedRoutes().orElseThrow();
        assertTrue(discarded.signum() >= 0);
        assertEquals(SearchSpace.completeRoutes(problem.size()), BigInteger.valueOf(evaluated).add(discarded));
    }

    private static void assertOverflow(TspProblem problem, TspResult result, long evaluated, long visited) {
        assertEquals(SolverStatus.ERRO, result.status());
        assertFalse(result.optimalityGuaranteed());
        assertTrue(result.reason().orElseThrow().contains("ArithmeticException"));
        assertTrue(result.reason().orElseThrow().contains("Long.MAX_VALUE"));
        assertMetrics(result, evaluated, visited);
        assertEquals(result.route().isPresent(), result.cost().isPresent());
        result.route().ifPresent(route ->
                assertEquals(RouteValidator.validateAndCost(problem, route), result.cost().orElseThrow()));
    }

    private static void assertMetrics(TspResult result, long evaluated, long visited) {
        assertEquals(evaluated, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(visited, result.metrics().visitedNodes().orElseThrow());
        assertTrue(result.metrics().elapsedNanos().orElseThrow() >= 0);
        assertTrue(result.metrics().discardedRoutes().orElseThrow().signum() >= 0);
    }

    private static void assertDeterministicMetrics(SolverMetrics expected, SolverMetrics actual) {
        assertEquals(expected.completeRoutesEvaluated(), actual.completeRoutesEvaluated());
        assertEquals(expected.visitedNodes(), actual.visitedNodes());
        assertEquals(expected.discardedRoutes(), actual.discardedRoutes());
    }

    private static List<Integer> firstRoute(int n) {
        List<Integer> indices = new ArrayList<>(IntStream.range(0, n).boxed().toList());
        indices.add(0);
        return indices;
    }

    private static TspProblem equalEdges(int n, long edgeCost) {
        long[][] costs = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                costs[i][j] = i == j ? 0 : edgeCost;
            }
        }
        return problem(costs);
    }

    private static TspProblem problem(long[][] costs) {
        List<City> cities = IntStream.range(0, costs.length).mapToObj(i -> new City(i, 0, 0, 0)).toList();
        return new TspProblem(cities, costs);
    }

    private static Stream<Arguments> differentialCases() {
        return IntStream.rangeClosed(3, 7).boxed().flatMap(n ->
                LongStream.concat(LongStream.range(0, 200), LongStream.of(-1, Long.MIN_VALUE, Long.MAX_VALUE, 657))
                        .mapToObj(seed -> Arguments.of(n, seed)));
    }
}
