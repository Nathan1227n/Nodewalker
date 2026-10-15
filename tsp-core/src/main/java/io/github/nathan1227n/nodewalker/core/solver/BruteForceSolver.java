package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.SearchSpace;
import io.github.nathan1227n.nodewalker.core.model.CostMath;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Solver exato de referência, sem poda, com origem fixa 0 e enumeração lexicográfica.
 * Uma execução concluída avalia todas as (N-1)! rotas, inclusive suas inversas.
 * O tempo é O(N * (N-1)!) e o espaço auxiliar da busca é O(N), sem copiar a matriz.
 * A complexidade fatorial não oferece garantia prática de desempenho para N grande.
 * O estado da busca é local a cada chamada; métricas não medidas ficam ausentes.
 */
public final class BruteForceSolver implements TspSolver {
    /**
     * Enumera todos os ciclos e conserva a primeira ótima, normalizando somente a saída.
     * Falhas de execução retornam ERRO com a contagem parcial e, quando disponível,
     * um incumbente completo válido; não declaram otimalidade. Erros da JVM são propagados.
     *
     * @param problem instância válida e imutável com origem fixa 0
     * @return resultado terminal com quantidade efetivamente avaliada
     * @throws NullPointerException se problem for nulo
     */
    @Override
    public TspResult solve(TspProblem problem) {
        Objects.requireNonNull(problem, "O problema TSP não pode ser nulo.");
        long evaluatedRoutes = 0;
        TspRoute bestRoute = null;
        long bestCost = 0;

        try {
            int[] permutation = new int[problem.size() - 1];
            for (int i = 0; i < permutation.length; i++) {
                permutation[i] = i + 1;
            }

            do {
                // Verifica a capacidade do contador antes de avaliar outra rota,
                // mas só publica o incremento depois de calcular o ciclo inteiro.
                long nextCount = Math.incrementExact(evaluatedRoutes);
                long cost = cycleCost(problem, permutation);
                evaluatedRoutes = nextCount;
                if (bestRoute == null || cost < bestCost) {
                    bestRoute = routeOf(permutation);
                    bestCost = cost;
                }
            } while (nextPermutation(permutation));
        } catch (RuntimeException failure) {
            return errorResult(problem, bestRoute, bestCost, evaluatedRoutes, failure);
        }

        try {
            if (!BigInteger.valueOf(evaluatedRoutes).equals(SearchSpace.completeRoutes(problem.size()))) {
                throw new IllegalStateException("A enumeração não cobriu todas as (N-1)! rotas.");
            }
            return TspResult.completed(problem, bestRoute.canonical(), bestCost, metrics(evaluatedRoutes));
        } catch (RuntimeException failure) {
            // Uma falha na conferência final não permite publicar esse incumbente.
            return errorResult(problem, null, 0, evaluatedRoutes, failure);
        }
    }

    private static long cycleCost(TspProblem problem, int[] permutation) {
        long cost = 0;
        int previous = 0;
        for (int city : permutation) {
            cost = CostMath.addExact(cost, problem.cost(previous, city));
            previous = city;
        }
        return CostMath.addExact(cost, problem.cost(previous, 0));
    }

    private static TspRoute routeOf(int[] permutation) {
        List<Integer> indices = new ArrayList<>(permutation.length + 2);
        indices.add(0);
        for (int city : permutation) {
            indices.add(city);
        }
        indices.add(0);
        return new TspRoute(indices);
    }

    private static boolean nextPermutation(int[] permutation) {
        int pivot = permutation.length - 2;
        while (pivot >= 0 && permutation[pivot] >= permutation[pivot + 1]) {
            pivot--;
        }
        if (pivot < 0) {
            return false;
        }

        int successor = permutation.length - 1;
        while (permutation[successor] <= permutation[pivot]) {
            successor--;
        }
        swap(permutation, pivot, successor);
        for (int left = pivot + 1, right = permutation.length - 1; left < right; left++, right--) {
            swap(permutation, left, right);
        }
        return true;
    }

    private static void swap(int[] values, int left, int right) {
        int temporary = values[left];
        values[left] = values[right];
        values[right] = temporary;
    }

    private static SolverMetrics metrics(long evaluatedRoutes) {
        return new SolverMetrics(OptionalLong.empty(), OptionalLong.of(evaluatedRoutes),
                OptionalLong.empty(), Optional.empty());
    }

    private static TspResult errorResult(TspProblem problem, TspRoute incumbent, long cost,
                                         long evaluatedRoutes, RuntimeException failure) {
        String reason = "Falha no BruteForceSolver: " + failure.getClass().getName();
        if (failure.getMessage() != null) {
            reason += ": " + failure.getMessage();
        }
        return TspResult.interrupted(problem, SolverStatus.ERRO, Optional.ofNullable(incumbent),
                incumbent == null ? OptionalLong.empty() : OptionalLong.of(cost), metrics(evaluatedRoutes), reason);
    }
}
