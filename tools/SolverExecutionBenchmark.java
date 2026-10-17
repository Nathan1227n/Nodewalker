import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.solver.BacktrackingSolver;
import io.github.nathan1227n.nodewalker.core.solver.BruteForceSolver;
import io.github.nathan1227n.nodewalker.core.solver.SolverAlgorithm;
import io.github.nathan1227n.nodewalker.core.solver.SolverCancellationToken;
import io.github.nathan1227n.nodewalker.core.solver.SolverExecutionOptions;
import io.github.nathan1227n.nodewalker.core.solver.SolverPreflight;
import io.github.nathan1227n.nodewalker.core.solver.SolverStatus;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.solver.TspSolver;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Experimento headless separado da CLI e da suíte de testes, sem asserts de desempenho.
 * Executar da raiz após compilar tsp-core; ver docs/CONTROLE_SOLVERS.md.
 */
public class SolverExecutionBenchmark {
    private static final long[] SEEDS = {0, 1, 657};
    private static final int WARMUP = 2;
    private static final int RUNS = 5;
    private static final SolverExecutionOptions OPTIONS = SolverExecutionOptions.limits(1_000_000, 2000);

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--examples")) {
            examples();
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Uso: sem argumentos para benchmark ou --examples para exemplos.");
        }
        System.out.println("Java=" + System.getProperty("java.version") + ", SO=" + System.getProperty("os.name")
                + ", arch=" + System.getProperty("os.arch") + ", nós=1000000, tempo=2000ms, warmup=2, runs=5");
        System.out.println("solver,N,seed,firstStatus,nodes,evaluated,discarded,medianNanos,cost");
        for (SolverAlgorithm algorithm : SolverAlgorithm.values()) {
            TspSolver solver = algorithm == SolverAlgorithm.BRUTE_FORCE ? new BruteForceSolver() : new BacktrackingSolver();
            int largestCompleted = 2;
            for (int n = 3; n <= 25; n++) {
                boolean allCompleted = true;
                for (long seed : SEEDS) {
                    TspProblem problem = TspGenerator.generate(n, seed, new GenerationOptions(100_000, 67_108_864));
                    boolean completed = true;
                    for (int warmup = 0; warmup < WARMUP; warmup++) {
                        completed &= solver.solve(problem, OPTIONS, new SolverCancellationToken()).status() == SolverStatus.CONCLUIDO;
                    }
                    long[] elapsed = new long[RUNS];
                    TspResult first = null;
                    for (int run = 0; run < RUNS; run++) {
                        TspResult result = solver.solve(problem, OPTIONS, new SolverCancellationToken());
                        elapsed[run] = result.metrics().elapsedNanos().orElseThrow();
                        if (first == null) {
                            first = result;
                        }
                        if (result.status() != SolverStatus.CONCLUIDO) {
                            completed = false;
                        } else if (first.status() == SolverStatus.CONCLUIDO
                                && (!first.route().equals(result.route()) || !first.cost().equals(result.cost()))) {
                            throw new IllegalStateException("Resultado inconsistente no benchmark");
                        }
                    }
                    Arrays.sort(elapsed);
                    System.out.println(algorithm + "," + n + "," + seed + "," + first.status() + ","
                            + first.metrics().visitedNodes().orElseThrow() + ","
                            + first.metrics().completeRoutesEvaluated().orElseThrow() + ","
                            + first.metrics().discardedRoutes().orElseThrow() + ","
                            + (completed ? elapsed[RUNS / 2] : "NA") + ","
                            + (first.cost().isPresent() ? first.cost().getAsLong() : "NA"));
                    allCompleted &= completed;
                }
                if (!allCompleted) {
                    break;
                }
                largestCompleted = n;
            }
            System.out.println("Faixa contínua concluída, todas as seeds/amostras: " + algorithm + " N<= " + largestCompleted);
        }
    }

    private static void examples() {
        var cities = IntStream.range(0, 3).mapToObj(i -> new City(i, 0, 0, 0)).toList();
        var triangle = new TspProblem(cities, new long[][] {{0, 10, 20}, {10, 0, 15}, {20, 15, 0}});
        var token = new SolverCancellationToken();
        token.cancel();
        show("pré-cancelado", new BruteForceSolver().solve(triangle, SolverExecutionOptions.nodeLimit(10), token));
        show("força bruta K=1", new BruteForceSolver().solve(triangle, SolverExecutionOptions.nodeLimit(1), new SolverCancellationToken()));
        show("força bruta K=2", new BruteForceSolver().solve(triangle, SolverExecutionOptions.nodeLimit(2), new SolverCancellationToken()));
        show("backtracking K=4", new BacktrackingSolver().solve(triangle, SolverExecutionOptions.nodeLimit(4), new SolverCancellationToken()));
        show("backtracking K=5", new BacktrackingSolver().solve(triangle, SolverExecutionOptions.nodeLimit(5), new SolverCancellationToken()));
        show("tempo=0", new BacktrackingSolver().solve(triangle, SolverExecutionOptions.timeLimitMillis(0), new SolverCancellationToken()));
        var zero25 = new TspProblem(IntStream.range(0, 25).mapToObj(i -> new City(i, 0, 0, 0)).toList(), new long[25][25]);
        for (int budget : new int[] {250, 300, 301}) {
            show("N=25 K=" + budget, new BacktrackingSolver().solve(zero25,
                    SolverExecutionOptions.nodeLimit(budget), new SolverCancellationToken()));
        }
        var preflight = SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, Integer.MAX_VALUE, SolverExecutionOptions.unlimited());
        System.out.println("Preflight N=" + preflight.cityCount() + ", permitido=" + preflight.executionAllowed()
                + ", exato=" + preflight.exactSearchSpace() + ", espaço >= " + preflight.theoreticalRoutes());
    }

    private static void show(String label, TspResult result) {
        System.out.println(label + ": " + result.status() + ", ótimo=" + result.optimalityGuaranteed()
                + ", nós=" + result.metrics().visitedNodes().orElseThrow()
                + ", avaliações=" + result.metrics().completeRoutesEvaluated().orElseThrow()
                + ", descartes=" + result.metrics().discardedRoutes().orElseThrow()
                + ", custo=" + (result.cost().isPresent() ? result.cost().getAsLong() : "ausente"));
    }
}
