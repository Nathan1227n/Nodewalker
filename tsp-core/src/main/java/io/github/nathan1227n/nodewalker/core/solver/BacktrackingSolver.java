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
 * Busca exata em profundidade, com origem 0, candidatos crescentes e poda por custo parcial.
 * Custos futuros não negativos tornam segura a poda partialCost >= bestCost quando há
 * incumbente; empates conservam a primeira ótima lexicográfica (DOC 03 §§9–10).
 * O pior caso é O(N * (N-1)!), com espaço auxiliar O(N), além da instância existente.
 * A pilha recursiva tem profundidade O(N); não há garantia prática para N grande nem
 * limite artificial de tamanho. Error, inclusive StackOverflowError, é propagado.
 */
public final class BacktrackingSolver implements TspSolver {
    private final LongSupplier clock;

    public BacktrackingSolver() {
        this(System::nanoTime);
    }

    BacktrackingSolver(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Resolve com estado privado por chamada, sem normalizar ou deduplicar durante a busca.
     * Reporta estados visitados, ciclos calculados, folhas descartadas e tempo interno.
     * Cada poda de profundidade depth representa (N-depth)! folhas, inclusive 0! na folha.
     * Falhas de execução retornam ERRO com diagnóstico,
     * métricas parciais e somente um incumbente anterior válido, quando disponível.
     *
     * @param problem instância válida e imutável com origem fixa 0
     * @return resultado terminal validado; CONCLUIDO somente após terminar a busca
     * @throws NullPointerException se problem for nulo
     */
    @Override
    public TspResult solve(TspProblem problem) {
        Objects.requireNonNull(problem, "O problema TSP não pode ser nulo.");
        SolverRunMetrics metrics = new SolverRunMetrics(clock);
        PruningHistogram pruning = null;
        Search search = null;
        try {
            metrics.start();
            pruning = new PruningHistogram(problem.size());
            search = new Search(problem, metrics, pruning);
            search.visit(1, 0);
            if (!search.hasIncumbent) {
                throw new IllegalStateException("A busca terminou sem encontrar um ciclo completo.");
            }
            TspRoute canonicalRoute = search.bestRoute.canonical();
            BigInteger discarded = pruning.discardedRoutes();
            metrics.requireCompleteCoverage(problem.size(), discarded);
            metrics.stop();
            return TspResult.completed(problem, canonicalRoute, search.bestCost, metrics.snapshot(Optional.of(discarded)));
        } catch (RuntimeException failure) {
            return metrics.errorResult("BacktrackingSolver", problem, search == null ? null : search.bestRoute,
                    search == null ? 0 : search.bestCost, pruning, failure);
        }
    }

    private static final class Search {
        private final TspProblem problem;
        private final int[] path;
        private final boolean[] visited;
        private final SolverRunMetrics metrics;
        private final PruningHistogram pruning;
        private boolean hasIncumbent;
        private TspRoute bestRoute;
        private long bestCost;

        private Search(TspProblem problem, SolverRunMetrics metrics, PruningHistogram pruning) {
            this.problem = problem;
            this.metrics = metrics;
            this.pruning = pruning;
            path = new int[problem.size()];
            visited = new boolean[problem.size()];
            path[0] = 0;
            visited[0] = true;
        }

        private void visit(int depth, long partialCost) {
            metrics.visit();

            // Toda continuação custa >= partialCost, inclusive o retorno à origem.
            // A igualdade não pode melhorar o incumbente e não substitui a primeira ótima.
            // Verifica também folhas antes de tentar somar a aresta final.
            if (hasIncumbent && partialCost >= bestCost) {
                pruning.record(depth);
                return;
            }
            if (depth == path.length) {
                // Reserva a capacidade do contador antes de calcular outro ciclo;
                // publica o incremento somente após somar o retorno sem overflow.
                long nextCount = metrics.nextEvaluationCount();
                long cost = CostMath.addExact(partialCost, problem.cost(path[depth - 1], 0));
                metrics.completeEvaluation(nextCount);
                if (!hasIncumbent || cost < bestCost) {
                    bestRoute = completeRoute();
                    bestCost = cost;
                    hasIncumbent = true;
                }
                return;
            }

            for (int city = 1; city < path.length; city++) {
                if (!visited[city]) {
                    long nextCost = CostMath.addExact(partialCost, problem.cost(path[depth - 1], city));
                    path[depth] = city;
                    visited[city] = true;
                    try {
                        visit(depth + 1, nextCost);
                    } finally {
                        visited[city] = false;
                    }
                }
            }
        }

        private TspRoute completeRoute() {
            List<Integer> indices = new ArrayList<>(path.length + 1);
            for (int city : path) {
                indices.add(city);
            }
            indices.add(0);
            return new TspRoute(indices);
        }
    }
}
