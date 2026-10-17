package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.model.CostMath;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Solver exato de referência, sem poda, com origem fixa 0 e enumeração lexicográfica.
 * Uma execução concluída avalia todas as (N-1)! rotas, inclusive suas inversas.
 * O tempo é O(N * (N-1)!) e o espaço auxiliar da busca é O(N), sem copiar a matriz.
 * A complexidade fatorial não oferece garantia prática de desempenho para N grande.
 * O estado da busca é local a cada chamada; métricas não medidas ficam ausentes.
 */
public final class BruteForceSolver implements TspSolver {
    private final LongSupplier clock;

    public BruteForceSolver() {
        this(System::nanoTime);
    }

    BruteForceSolver(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

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
        SolverRunMetrics metrics = new SolverRunMetrics(clock);
        TspRoute bestRoute = null;
        long bestCost = 0;

        try {
            metrics.start();
            int[] permutation = new int[problem.size() - 1];
            for (int i = 0; i < permutation.length; i++) {
                permutation[i] = i + 1;
            }

            do {
                // Um estado observado é uma permutação cujo processamento começou,
                // inclusive se seu custo falhar; não há árvore recursiva neste algoritmo.
                metrics.visit();
                // Verifica a capacidade do contador antes de avaliar outra rota,
                // mas só publica o incremento depois de calcular o ciclo inteiro.
                long nextCount = metrics.nextEvaluationCount();
                long cost = cycleCost(problem, permutation);
                metrics.completeEvaluation(nextCount);
                if (bestRoute == null || cost < bestCost) {
                    bestRoute = routeOf(permutation);
                    bestCost = cost;
                }
            } while (nextPermutation(permutation));
            metrics.requireCompleteCoverage(problem.size(), BigInteger.ZERO);
            TspRoute canonicalRoute = bestRoute.canonical();
            metrics.stop();
            return TspResult.completed(problem, canonicalRoute, bestCost, metrics.snapshot(Optional.of(BigInteger.ZERO)));
        } catch (RuntimeException failure) {
            return metrics.errorResult("BruteForceSolver", problem, bestRoute, bestCost, null, failure);
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
}
