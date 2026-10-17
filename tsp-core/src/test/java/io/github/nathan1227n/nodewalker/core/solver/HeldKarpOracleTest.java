package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Valida o próprio oráculo por referências literais/analíticas, sem executar solvers. */
class HeldKarpOracleTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("knownOptima")
    void matchesKnownCostsAndFirstLexicographicCyclesDespiteDescendingDpOrder(String label, TspProblem problem,
                                                                            long cost, List<Integer> route) {
        var result = HeldKarpOracle.solve(problem);
        var details = SolverAuditAssertions.diagnostics(label, problem, () -> result, Map.of());
        assertEquals(BigInteger.valueOf(cost), result.cost(), details);
        assertEquals(route, result.route(), details);
        assertEquals(cost, result.longCost().orElseThrow(), details);
        assertEquals(result.cost(), SolverAuditAssertions.cycleCost(problem, result.route(), details), details);
        assertEquals(result.cost(), SolverAuditAssertions.cycleCost(problem, result.route().reversed(), details), details);
        assertTrue(result.route().get(1) < result.route().get(problem.size() - 1), details);
    }

    @Test
    void maximumTwelveCityInstanceRunsOnlyHeldKarpWithAnAnalyticallyKnownOptimum() {
        // 12 arestas >= 1 impõem limite inferior 12. O anel ascendente o atinge.
        var problem = ContractTestProblems.unitRing(12);
        var result = HeldKarpOracle.solve(problem);
        var details = SolverAuditAssertions.diagnostics("fronteira N=12, somente DP", problem, () -> result, Map.of());
        assertEquals(12, HeldKarpOracle.MAX_CITIES);
        assertEquals(BigInteger.valueOf(12), result.cost(), details);
        assertEquals(firstRoute(12), result.route(), details);
        assertEquals(result.cost(), SolverAuditAssertions.cycleCost(problem, result.route(), details), details);
    }

    @Test
    void rejectsThirteenCitiesBeforeTheExponentialAllocationAndRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> HeldKarpOracle.solve(ContractTestProblems.unitRing(13)));
        assertThrows(NullPointerException.class, () -> HeldKarpOracle.solve(null));
    }

    @ParameterizedTest
    @MethodSource("largeCosts")
    void representsMathematicalCostsBeyondLongWithoutWrapOrInfinitySentinels(TspProblem problem,
                                                                           BigInteger expected, boolean fitsLong) {
        var result = HeldKarpOracle.solve(problem);
        var details = SolverAuditAssertions.diagnostics("fronteiras long", problem, () -> result, Map.of());
        assertEquals(expected, result.cost(), details);
        assertEquals(fitsLong, result.longCost().isPresent(), details);
        if (fitsLong) {
            assertEquals(expected.longValueExact(), result.longCost().orElseThrow(), details);
        }
        assertEquals(result.cost(), SolverAuditAssertions.cycleCost(problem, result.route(), details), details);
        assertEquals(List.of(0, 1, 2, 0), result.route(), details);
    }

    @Test
    void returnsImmutableIndependentResultsAndRepeatsTheKnownSeedReference() {
        var problem = TspGenerator.generate(4, 657);
        var first = HeldKarpOracle.solve(problem);
        var details = SolverAuditAssertions.diagnostics("seed reproduzível", problem, () -> first, Map.of());
        HeldKarpOracle.solve(ContractTestProblems.distinctTiedCycles());
        assertEquals(BigInteger.valueOf(2799), first.cost(), details);
        assertEquals(List.of(0, 2, 1, 3, 0), first.route(), details);
        assertEquals(first, HeldKarpOracle.solve(problem), details);
        assertThrows(UnsupportedOperationException.class, () -> first.route().set(1, 3));
    }

    static Stream<Arguments> knownOptima() {
        return Stream.of(
                Arguments.of("TSP-01 triângulo fechado", ContractTestProblems.triangle(), 45L, List.of(0, 1, 2, 0)),
                Arguments.of("TSP-02 DOC 03 §7", ContractTestProblems.fourCityReference(), 300L, List.of(0, 1, 2, 3, 0)),
                Arguments.of("TSP-03/04 zero N=3", ContractTestProblems.equalEdges(3, 0), 0L, firstRoute(3)),
                Arguments.of("TSP-03/04 zero N=6", ContractTestProblems.equalEdges(6, 0), 0L, firstRoute(6)),
                Arguments.of("TSP-03/04 empate positivo", ContractTestProblems.equalEdges(5, 7), 35L, firstRoute(5)),
                Arguments.of("TSP-04 ciclos distintos empatados", ContractTestProblems.distinctTiedCycles(),
                        12L, List.of(0, 1, 3, 2, 0)),
                Arguments.of("primeira permutação não é ótima", ContractTestProblems.lateFirstOptimum(),
                        4L, List.of(0, 2, 1, 3, 0)));
    }

    static Stream<Arguments> largeCosts() {
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE);
        return Stream.of(
                Arguments.of(ContractTestProblems.triangle(0, 0, 0), BigInteger.ZERO, true),
                Arguments.of(ContractTestProblems.triangle(Long.MAX_VALUE, 0, 0), maximum, true),
                Arguments.of(ContractTestProblems.triangle(Long.MAX_VALUE, 1, 0), maximum.add(BigInteger.ONE), false),
                Arguments.of(ContractTestProblems.triangle(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE),
                        new BigInteger("27670116110564327421"), false));
    }

    private static List<Integer> firstRoute(int n) {
        return Stream.concat(IntStream.range(0, n).boxed(), Stream.of(0)).toList();
    }
}
