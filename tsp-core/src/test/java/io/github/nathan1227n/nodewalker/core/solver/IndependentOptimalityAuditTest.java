package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class IndependentOptimalityAuditTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("io.github.nathan1227n.nodewalker.core.solver.HeldKarpOracleTest#knownOptima")
    void auditsBothSolversAgainstIndependentManualReferences(String label, TspProblem problem,
                                                             long knownCost, List<Integer> knownRoute) {
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics(label, problem, () -> reference, results);
        assertEquals(BigInteger.valueOf(knownCost), reference.cost(), details);
        assertEquals(knownRoute, reference.route(), details);
        results.values().forEach(result -> SolverAuditAssertions.assertCompleted(problem, reference, result, details));
    }

    @Test
    void maximumLongOptimumIsPublishableAndAgreesInCostAndCanonicalRoute() {
        var problem = ContractTestProblems.triangle(Long.MAX_VALUE, 0, 0);
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("ótimo Long.MAX_VALUE", problem, () -> reference, results);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), reference.cost(), details);
        results.values().forEach(result -> SolverAuditAssertions.assertCompleted(problem, reference, result, details));
    }

    @Test
    void mathematicalOptimumBeyondLongIsNotAPublishableSolverResult() {
        var problem = ContractTestProblems.triangle(Long.MAX_VALUE, 1, 0);
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("ótimo matemático fora de long", problem, () -> reference, results);
        assertEquals(new BigInteger("9223372036854775808"), reference.cost(), details);
        assertTrue(reference.longCost().isEmpty(), details);
        results.values().forEach(result -> {
            SolverAuditAssertions.assertInterrupted(problem, reference, result, SolverStatus.ERRO, details);
            assertTrue(result.route().isEmpty(), details);
            assertEquals(0, result.metrics().completeRoutesEvaluated().orElseThrow(), details);
            assertTrue(result.reason().orElseThrow().contains("ArithmeticException"), details);
        });
    }

    @Test
    void twoErrorsDoNotProveARepresentableMathematicalOptimum() {
        var problem = ContractTestProblems.problem(new long[][] {
                {0, Long.MAX_VALUE, 1, 1}, {Long.MAX_VALUE, 0, 1, 1}, {1, 1, 0, 1}, {1, 1, 1, 0}
        });
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("overflow antes do ótimo representável", problem, () -> reference, results);
        assertEquals(BigInteger.valueOf(4), reference.cost(), details);
        assertEquals(List.of(0, 2, 1, 3, 0), reference.route(), details);
        assertEquals(4, reference.longCost().orElseThrow(), details);
        results.values().forEach(result -> {
            SolverAuditAssertions.assertInterrupted(problem, reference, result, SolverStatus.ERRO, details);
            assertTrue(result.route().isEmpty(), details);
            assertTrue(result.reason().orElseThrow().contains("ArithmeticException"), details);
            // A auditoria de conclusão precisa rejeitar concordância apenas no estado ERRO.
            assertThrows(AssertionError.class, () -> SolverAuditAssertions.assertCompleted(problem, reference, result, details));
        });
    }

    @Test
    void preservesALongMaximumIncumbentWhileTheMathematicalOptimumIsOnlyOne() {
        var problem = ContractTestProblems.problem(new long[][] {
                {0, Long.MAX_VALUE, 0, 0}, {Long.MAX_VALUE, 0, 0, 1}, {0, 0, 0, 0}, {0, 1, 0, 0}
        });
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("incumbente MAX, ótimo matemático 1", problem, () -> reference, results);
        assertEquals(BigInteger.ONE, reference.cost(), details);
        assertEquals(List.of(0, 2, 1, 3, 0), reference.route(), details);
        results.values().forEach(result -> {
            SolverAuditAssertions.assertInterrupted(problem, reference, result, SolverStatus.ERRO, details);
            assertEquals(Long.MAX_VALUE, result.cost().orElseThrow(), details);
            assertEquals(1, result.metrics().completeRoutesEvaluated().orElseThrow(), details);
        });
    }

    @Test
    void backtrackingMayProveTheOptimumWithoutObservingAnOverflowThatBruteForceMustEvaluate() {
        var problem = ContractTestProblems.problem(new long[][] {
                {0, 1, Long.MAX_VALUE, 1}, {1, 0, 1, 2}, {Long.MAX_VALUE, 1, 0, 1}, {1, 2, 1, 0}
        });
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("overflow observado versus ramo podado", problem, () -> reference, results);
        assertEquals(BigInteger.valueOf(4), reference.cost(), details);
        SolverAuditAssertions.assertCompleted(problem, reference, results.get("BacktrackingSolver"), details);
        SolverAuditAssertions.assertInterrupted(problem, reference, results.get("BruteForceSolver"), SolverStatus.ERRO, details);
        assertTrue(results.get("BruteForceSolver").reason().orElseThrow().contains("ArithmeticException"), details);
    }

    enum StopMode { NODES, TIME, CANCEL }

    @ParameterizedTest
    @EnumSource(StopMode.class)
    void interruptedSearchKeepsAValidButStrictlySuboptimalIncumbent(StopMode mode) {
        var problem = ContractTestProblems.lateFirstOptimum();
        var reference = HeldKarpOracle.solve(problem);
        var results = new LinkedHashMap<String, TspResult>();
        for (boolean backtracking : new boolean[] {false, true}) {
            var token = new SolverCancellationToken();
            var clock = new AtomicLong();
            SolverExecutionControl.Observer observer = (phase, nodes, evaluated) -> {
                if (evaluated == 1) {
                    if (mode == StopMode.CANCEL) {
                        token.cancel();
                    } else if (mode == StopMode.TIME) {
                        clock.set(1_000_000);
                    }
                }
            };
            TspSolver solver = backtracking ? new BacktrackingSolver(clock::get, observer)
                    : new BruteForceSolver(clock::get, observer);
            var options = switch (mode) {
                case NODES -> SolverExecutionOptions.nodeLimit(backtracking ? 4 : 1);
                case TIME -> SolverExecutionOptions.timeLimitMillis(1);
                case CANCEL -> SolverExecutionOptions.nodeLimit(1000);
            };
            results.put(backtracking ? "BacktrackingSolver" : "BruteForceSolver", solver.solve(problem, options, token));
        }
        var details = SolverAuditAssertions.diagnostics("TSP-09/10: " + mode
                + ", nós BF=1/BT=4 ou prazo=1ms ou token após 1 ciclo", problem, () -> reference, results);
        assertEquals(BigInteger.valueOf(4), reference.cost(), details);
        results.values().forEach(result -> {
            SolverAuditAssertions.assertInterrupted(problem, reference, result,
                    mode == StopMode.CANCEL ? SolverStatus.CANCELADO : SolverStatus.LIMITE_ATINGIDO, details);
            assertEquals(20, result.cost().orElseThrow(), details);
            assertEquals(List.of(0, 1, 2, 3, 0), result.route().orElseThrow().indices(), details);
            assertEquals(1, result.metrics().completeRoutesEvaluated().orElseThrow(), details);
            assertEquals(BigInteger.ZERO, result.metrics().discardedRoutes().orElseThrow(), details);
        });
    }

    @Test
    void diagnosticsAreLazyCompleteAndContainBothResultsWhenAnAssertionFails() {
        var problem = ContractTestProblems.fourCityReference();
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var calls = new AtomicInteger();
        var details = SolverAuditAssertions.diagnostics("diagnóstico reproduzível", problem, () -> {
            calls.incrementAndGet();
            return reference;
        }, results);
        SolverAuditAssertions.assertCompleted(problem, reference, results.get("BruteForceSolver"), details);
        assertEquals(0, calls.get());
        AssertionError error = assertThrows(AssertionError.class, () -> assertEquals(301,
                results.get("BruteForceSolver").cost().orElseThrow(), details));
        assertEquals(1, calls.get());
        String message = error.getMessage();
        for (String field : List.of("N=4", "seed=ausente", "Matriz de custos", "80, 0, 70, 110",
                "BruteForceSolver", "BacktrackingSolver", "estado=CONCLUIDO", "cost=300", "route=[0, 1, 2, 3, 0]",
                "métricas=SolverMetrics", "motivo=")) {
            assertTrue(message.contains(field), field);
        }
        var seeded = TspProblem.copyOf(problem.cities(), new long[][] {
                {0, 80, 100, 90}, {80, 0, 70, 110}, {100, 70, 0, 60}, {90, 110, 60, 0}
        }, 657, () -> { });
        assertTrue(SolverAuditAssertions.diagnostics("seed explícita", seeded, () -> reference, results).get().contains("seed=657"));
    }

    @Test
    void completedRunsKeepDeterministicCountersWhileDurationIsCheckedSeparately() {
        var problem = ContractTestProblems.distinctTiedCycles();
        var reference = HeldKarpOracle.solve(problem);
        var first = runBoth(problem);
        var repeated = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("repetição, elapsedNanos independente", problem, () -> reference, repeated);
        for (String name : first.keySet()) {
            var before = first.get(name);
            var after = repeated.get(name);
            SolverAuditAssertions.assertCompleted(problem, reference, before, details);
            SolverAuditAssertions.assertCompleted(problem, reference, after, details);
            assertEquals(before.metrics().visitedNodes(), after.metrics().visitedNodes(), details);
            assertEquals(before.metrics().completeRoutesEvaluated(), after.metrics().completeRoutesEvaluated(), details);
            assertEquals(before.metrics().discardedRoutes(), after.metrics().discardedRoutes(), details);
            assertTrue(before.metrics().elapsedNanos().orElseThrow() >= 0, details);
            assertTrue(after.metrics().elapsedNanos().orElseThrow() >= 0, details);
        }
    }

    @Test
    void actualDiscardCountMatchesDisjointExplicitLeavesOfKnownPrunedPrefixes() {
        var problem = ContractTestProblems.problem(new long[][] {
                {0, 1, 100, 1}, {1, 0, 1, 100}, {100, 1, 0, 1}, {1, 100, 1, 0}
        });
        var reference = HeldKarpOracle.solve(problem);
        var results = runBoth(problem);
        var details = SolverAuditAssertions.diagnostics("TSP-08, prefixos 013/02/031", problem, () -> reference, results);
        results.values().forEach(result -> SolverAuditAssertions.assertCompleted(problem, reference, result, details));
        assertEquals(BigInteger.valueOf(4), reference.cost(), details);
        assertEquals(List.of(0, 1, 2, 3, 0), reference.route(), details);

        // Conjuntos literais de folhas: nenhum contador/histograma gera esta referência.
        Map<List<Integer>, List<List<Integer>>> eliminatedByPrefix = Map.of(
                List.of(0, 1, 3), List.of(List.of(0, 1, 3, 2, 0)),
                List.of(0, 2), List.of(List.of(0, 2, 1, 3, 0), List.of(0, 2, 3, 1, 0)),
                List.of(0, 3, 1), List.of(List.of(0, 3, 1, 2, 0)));
        var eliminated = new HashSet<List<Integer>>();
        eliminatedByPrefix.forEach((prefix, cycles) -> {
            BigInteger partial = BigInteger.ZERO;
            for (int i = 1; i < prefix.size(); i++) {
                assertTrue(partial.compareTo(reference.cost()) < 0, details); // ancestrais não são podados
                partial = partial.add(BigInteger.valueOf(problem.cost(prefix.get(i - 1), prefix.get(i))));
            }
            assertTrue(partial.compareTo(reference.cost()) >= 0, details);
            assertEquals(SolverAuditAssertions.factorial(problem.size() - prefix.size()),
                    BigInteger.valueOf(cycles.size()), details);
            for (var cycle : cycles) {
                assertEquals(prefix, cycle.subList(0, prefix.size()), details);
                assertTrue(eliminated.add(cycle), details); // folhas disjuntas
                assertTrue(SolverAuditAssertions.cycleCost(problem, cycle, details)
                        .compareTo(reference.cost()) > 0, details);
            }
        });
        assertEquals(4, eliminated.size(), details);
        assertEquals(BigInteger.valueOf(eliminated.size()), results.get("BacktrackingSolver")
                .metrics().discardedRoutes().orElseThrow(), details);
        assertEquals(2, results.get("BacktrackingSolver").metrics().completeRoutesEvaluated().orElseThrow(), details);
        assertEquals(10, results.get("BacktrackingSolver").metrics().visitedNodes().orElseThrow(), details);
        assertEquals(6, results.get("BruteForceSolver").metrics().completeRoutesEvaluated().orElseThrow(), details);
        assertEquals(BigInteger.ZERO, results.get("BruteForceSolver").metrics().discardedRoutes().orElseThrow(), details);
    }

    private static Map<String, TspResult> runBoth(TspProblem problem) {
        return Map.of("BruteForceSolver", new BruteForceSolver().solve(problem),
                "BacktrackingSolver", new BacktrackingSolver().solve(problem));
    }
}
