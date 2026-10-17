package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.SearchSpace;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Asserções externas ao oráculo; a referência nunca depende de um resultado do solver. */
final class SolverAuditAssertions {
    private SolverAuditAssertions() {
    }

    static Supplier<String> diagnostics(String scenario, TspProblem problem, Supplier<?> reference,
                                        Map<String, TspResult> results) {
        return () -> {
            StringBuilder message = new StringBuilder("\nCaso: ").append(scenario)
                    .append("\nN=").append(problem.size()).append(", seed=")
                    .append(problem.seed().isPresent() ? problem.seed().getAsLong() : "ausente (matriz manual)")
                    .append("\nReferência: ").append(reference.get()).append("\nMatriz de custos:");
            for (int from = 0; from < problem.size(); from++) {
                message.append("\n[");
                for (int to = 0; to < problem.size(); to++) {
                    if (to > 0) {
                        message.append(", ");
                    }
                    message.append(problem.cost(from, to));
                }
                message.append(']');
            }
            results.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                TspResult result = entry.getValue();
                message.append('\n').append(entry.getKey()).append(": estado=").append(result.status())
                        .append(", ótimoDeclarado=").append(result.optimalityGuaranteed())
                        .append(", custo=").append(result.cost()).append(", rota=")
                        .append(result.route().map(route -> route.indices().toString()).orElse("ausente"))
                        .append(", métricas=").append(result.metrics()).append(", motivo=").append(result.reason());
            });
            return message.toString();
        };
    }

    static void assertCompleted(TspProblem problem, HeldKarpOracle.Optimum reference,
                                TspResult result, Supplier<String> details) {
        assertEquals(SolverStatus.CONCLUIDO, result.status(), details);
        assertTrue(result.optimalityGuaranteed(), details);
        assertTrue(result.reason().isEmpty(), details);
        assertTrue(reference.longCost().isPresent(), details);
        assertEquals(reference.route(), result.route().orElseThrow().indices(), details);
        assertEquals(reference.cost(), BigInteger.valueOf(result.cost().orElseThrow()), details);
        assertIncumbent(problem, reference.cost(), result, details);
        assertTrue(result.route().orElseThrow().isCanonical(), details);
        assertEquals(RouteValidator.validateAndCost(problem, result.route().orElseThrow()),
                result.cost().orElseThrow(), details);
        BigInteger expectedSpace = factorial(problem.size() - 1);
        long evaluated = result.metrics().completeRoutesEvaluated().orElseThrow();
        assertTrue(evaluated >= 1 && BigInteger.valueOf(evaluated).compareTo(expectedSpace) <= 0, details);
        assertTrue(result.metrics().visitedNodes().orElseThrow() >= evaluated, details);
        assertEquals(expectedSpace, SearchSpace.completeRoutes(problem.size()), details);
        assertEquals(expectedSpace, BigInteger.valueOf(result.metrics().completeRoutesEvaluated().orElseThrow())
                .add(result.metrics().discardedRoutes().orElseThrow()), details);
        assertTrue(result.metrics().elapsedNanos().orElseThrow() >= 0, details);
    }

    static void assertInterrupted(TspProblem problem, HeldKarpOracle.Optimum reference,
                                  TspResult result, SolverStatus status, Supplier<String> details) {
        assertTrue(status.isTerminal() && status != SolverStatus.CONCLUIDO, details);
        assertEquals(status, result.status(), details);
        assertFalse(result.optimalityGuaranteed(), details);
        assertTrue(result.reason().isPresent() && !result.reason().orElseThrow().isBlank(), details);
        assertIncumbent(problem, reference.cost(), result, details);
    }

    static void assertIncumbent(TspProblem problem, BigInteger mathematicalMinimum,
                                TspResult result, Supplier<String> details) {
        assertEquals(result.route().isPresent(), result.cost().isPresent(), details);
        if (result.route().isPresent()) {
            BigInteger actualCost = cycleCost(problem, result.route().orElseThrow().indices(), details);
            assertEquals(BigInteger.valueOf(result.cost().orElseThrow()), actualCost, details);
            assertTrue(actualCost.compareTo(mathematicalMinimum) >= 0, details);
        }
    }

    /** Validação estrutural e soma BigInteger independentes, incluindo a última aresta. */
    static BigInteger cycleCost(TspProblem problem, List<Integer> route, Supplier<String> details) {
        int n = problem.size();
        assertEquals(n + 1, route.size(), details);
        assertEquals(0, route.getFirst(), details);
        assertEquals(0, route.getLast(), details);
        boolean[] seen = new boolean[n];
        BigInteger cost = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            int from = route.get(i);
            assertTrue(from >= 0 && from < n, details);
            assertFalse(seen[from], details);
            seen[from] = true;
            int to = route.get(i + 1);
            assertTrue(to >= 0 && to < n, details);
            cost = cost.add(BigInteger.valueOf(problem.cost(from, to)));
        }
        return cost;
    }

    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    /**
     * Matriz zero: após a primeira rota, grupos de r podas eliminam r*r! folhas.
     * Com p podas, retirar grupos inteiros de 1,2,... deixa t podas de peso r!.
     * A soma telescópica é (t+1)*r! - 1. O esperado deriva do orçamento, não das métricas.
     */
    static BigInteger zeroCostDiscardedRoutes(int n, long nodeBudget) {
        long totalNodes = 1 + n * (n - 1L) / 2;
        long prunes = Math.min(nodeBudget, totalNodes) - n;
        if (prunes <= 0) {
            return BigInteger.ZERO;
        }
        int remaining = 1;
        while (prunes >= remaining) {
            prunes -= remaining++;
        }
        return factorial(remaining).multiply(BigInteger.valueOf(prunes + 1)).subtract(BigInteger.ONE);
    }
}
