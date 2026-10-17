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
    private final SolverExecutionControl.Observer observer;

    public BacktrackingSolver() {
        this(System::nanoTime);
    }

    BacktrackingSolver(LongSupplier clock) {
        this(clock, null);
    }

    BacktrackingSolver(LongSupplier clock, SolverExecutionControl.Observer observer) {
        this.clock = Objects.requireNonNull(clock);
        this.observer = observer;
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
        return solveInternal(problem, null, null);
    }

    @Override
    public TspResult solve(TspProblem problem, SolverExecutionOptions options, SolverCancellationToken token) {
        Objects.requireNonNull(problem, "O problema TSP não pode ser nulo.");
        Objects.requireNonNull(options, "As opções não podem ser nulas.");
        Objects.requireNonNull(token, "O token não pode ser nulo.");
        SolverPreflight.analyze(SolverAlgorithm.BACKTRACKING, problem.size(), options).requireAllowed();
        return solveInternal(problem, options, token);
    }

    private TspResult solveInternal(TspProblem problem, SolverExecutionOptions options, SolverCancellationToken token) {
        Objects.requireNonNull(problem, "O problema TSP não pode ser nulo.");
        SolverRunMetrics metrics = new SolverRunMetrics(clock);
        SolverExecutionControl control = new SolverExecutionControl(metrics, options, token, observer);
        PruningHistogram pruning = null;
        Search search = null;
        try {
            metrics.start();
            control.checkpoint(SolverExecutionControl.Phase.PREPARATION);
            pruning = new PruningHistogram(problem.size());
            search = new Search(problem, metrics, pruning, control);
            search.visit(1, 0);
            if (!search.hasIncumbent) {
                throw new IllegalStateException("A busca terminou sem encontrar um ciclo completo.");
            }
            TspRoute canonicalRoute = search.bestRoute.canonical();
            BigInteger discarded = pruning.discardedRoutes();
            metrics.requireCompleteCoverage(problem.size(), discarded);
            metrics.stop();
            control.checkpoint(SolverExecutionControl.Phase.FINALIZATION);
            return TspResult.completed(problem, canonicalRoute, search.bestCost, metrics.snapshot(Optional.of(discarded)));
        } catch (SolverExecutionControl.Stopped stopped) {
            return metrics.interruptedResult(problem, search == null ? null : search.bestRoute,
                    search == null ? 0 : search.bestCost, pruning, stopped.status(), stopped.getMessage());
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
        private final SolverExecutionControl control;
        private boolean hasIncumbent;
        private TspRoute bestRoute;
        private long bestCost;

        private Search(TspProblem problem, SolverRunMetrics metrics, PruningHistogram pruning,
                       SolverExecutionControl control) {
            this.problem = problem;
            this.metrics = metrics;
            this.pruning = pruning;
            this.control = control;
            path = new int[problem.size()];
            visited = new boolean[problem.size()];
            path[0] = 0;
            visited[0] = true;
        }

        private void visit(int depth, long partialCost) {
            control.checkpoint(SolverExecutionControl.Phase.BEFORE_NODE);
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
                control.checkpoint(SolverExecutionControl.Phase.EDGE);
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
                control.checkpoint(SolverExecutionControl.Phase.ITERATION);
                if (!visited[city]) {
                    // Reserva operacional antes da soma: um filho impedido não calcula custo nem conta nó.
                    control.checkpoint(SolverExecutionControl.Phase.BEFORE_DESCENT);
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
