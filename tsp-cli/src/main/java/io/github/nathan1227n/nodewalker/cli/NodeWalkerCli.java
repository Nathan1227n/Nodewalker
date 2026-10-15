package io.github.nathan1227n.nodewalker.cli;

import io.github.nathan1227n.nodewalker.core.generator.GenerationCancelledException;
import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.solver.BruteForceSolver;
import io.github.nathan1227n.nodewalker.core.solver.SolverMetrics;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.solver.TspSolver;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** Ponto de entrada sem JavaFX. Tempos externos não alteram SolverMetrics. */
public final class NodeWalkerCli {
    private static final String NOT_REPORTED = "não informado";
    private static final String USAGE = """
            Uso: java -jar nodewalker-cli.jar --n N --seed SEED --solver brute-force [opções]
              --n N                         inteiro >= 3 (obrigatório)
              --seed SEED                   inteiro long, inclusive negativo (obrigatório)
              --solver brute-force          único solver implementado (obrigatório)
              --warmup W                    aquecimentos >= 0; padrão: 3
              --runs R                      medições >= 1; padrão: 5
              --max-total-routes T           teto da bateria, incluindo warm-up; padrão: 10000000
              --max-candidates C             candidatos da geração; padrão: 100000
              --max-estimated-bytes B        bytes estimados da geração; padrão: 67108864
              --help                        mostra esta ajuda, sem executar
            Os três orçamentos são long positivos. Use opções no formato --opção valor.
            O teto é verificado antes da busca; não é limite de tempo nem cancelamento do solver.
            """;

    private final Generator generator;
    private final TspSolver solver;
    private final LongSupplier clock;

    NodeWalkerCli(Generator generator, TspSolver solver, LongSupplier clock) {
        this.generator = Objects.requireNonNull(generator);
        this.solver = Objects.requireNonNull(solver);
        this.clock = Objects.requireNonNull(clock);
    }

    public static void main(String[] args) {
        int exit = new NodeWalkerCli(TspGenerator::generate, new BruteForceSolver(), System::nanoTime)
                .run(args, new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                        new PrintWriter(System.err, true, StandardCharsets.UTF_8));
        if (exit != 0) {
            System.exit(exit);
        }
    }

    int run(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length == 1 && "--help".equals(args[0])) {
            out.print(USAGE);
            out.flush();
            return 0;
        }
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException failure) {
            err.println("Argumentos inválidos: " + failure.getMessage());
            err.print(USAGE);
            err.flush();
            return 2;
        }
        printOptions(out, options);
        try {
            Preflight preflight = Preflight.check(options);
            out.println("Memória de geração estimada (bytes): " + preflight.estimatedBytes());
            out.println("Espaço teórico (N-1)!: " + preflight.routes());
            out.println("Avaliações previstas na bateria (warm-up + medições): " + preflight.totalRoutes());
            out.flush();
        } catch (IllegalArgumentException failure) {
            err.println("Preflight recusado: " + failure.getMessage());
            err.flush();
            return 2;
        }

        TspProblem problem;
        GenerationOptions generationOptions = new GenerationOptions(options.maxCandidates(), options.maxEstimatedBytes());
        try {
            long start = clock.getAsLong();
            problem = generator.generate(options.n(), options.seed(), generationOptions);
            long end = clock.getAsLong();
            long elapsed = end - start;
            Objects.requireNonNull(problem, "O gerador retornou null.");
            if (elapsed < 0) {
                throw new IllegalStateException("Duração negativa da geração.");
            }
            out.println("Tempo de geração (ns): " + elapsed);
            out.flush();
        } catch (GenerationCancelledException failure) {
            err.println("Geração cancelada: " + diagnostic(failure));
            err.flush();
            return 130;
        } catch (RuntimeException failure) {
            err.println("Falha na geração: " + diagnostic(failure));
            err.flush();
            return 1;
        }

        try {
            BenchmarkRunner.Report report = new BenchmarkRunner(clock).run(problem, solver, options.warmup(), options.runs());
            out.println("Benchmark: CONCLUIDO");
            out.println("Seed da instância: " + number(problem.seed()));
            printResult(out, report.result(), "Métricas da primeira execução medida");
            out.println("Durações do solver (ns), na ordem de execução: " + Arrays.toString(report.durationsNanos()));
            out.println("Mediana do solver (ns): " + report.medianNanos().toPlainString());
            out.println("Mediana do solver (ms): " + report.medianNanos().movePointLeft(6).toPlainString());
            out.flush();
            return 0;
        } catch (BenchmarkRunner.Failure failure) {
            err.println("Benchmark: ERRO");
            err.println(failure.getMessage());
            if (failure.result() != null) {
                printResult(err, failure.result(), "Métricas do resultado que encerrou a bateria");
            }
        } catch (RuntimeException failure) {
            err.println("Benchmark: ERRO");
            err.println(diagnostic(failure));
        }
        err.println("Mediana do solver (ns): não calculada.");
        err.flush();
        return 1;
    }

    private static void printOptions(PrintWriter out, CliOptions options) {
        out.println("NodeWalker — benchmark sem JavaFX");
        out.println("Java: " + System.getProperty("java.version") + "; SO: " + System.getProperty("os.name")
                + "; arquitetura: " + System.getProperty("os.arch"));
        out.println("N: " + options.n() + "; origem: 0; seed: " + options.seed() + "; solver: " + options.solver());
        out.println("Aquecimentos: " + options.warmup() + "; medições: " + options.runs());
        out.println("Orçamento total de avaliações: " + options.maxTotalRoutes());
        out.println("Orçamentos da geração: candidatos=" + options.maxCandidates()
                + "; bytes estimados=" + options.maxEstimatedBytes());
        out.flush();
    }

    private static void printResult(PrintWriter out, TspResult result, String metricsLabel) {
        out.println("Estado do solver: " + result.status());
        String routeLabel = result.optimalityGuaranteed() ? "Rota: " : "Rota incumbente (sem ótimo comprovado): ";
        out.println(routeLabel + result.route().map(route -> route.indices().stream()
                .map(String::valueOf).collect(Collectors.joining(" -> "))).orElse(NOT_REPORTED));
        out.println("Custo: " + number(result.cost()));
        result.reason().ifPresent(reason -> out.println("Diagnóstico: " + reason));
        SolverMetrics metrics = result.metrics();
        out.println(metricsLabel + ":");
        out.println("  completeRoutesEvaluated: " + number(metrics.completeRoutesEvaluated()));
        out.println("  elapsedNanos: " + number(metrics.elapsedNanos()));
        out.println("  visitedNodes: " + number(metrics.visitedNodes()));
        out.println("  discardedRoutes: " + metrics.discardedRoutes().map(Object::toString).orElse(NOT_REPORTED));
    }

    private static String number(OptionalLong value) {
        return value.isPresent() ? Long.toString(value.getAsLong()) : NOT_REPORTED;
    }

    private static String diagnostic(RuntimeException failure) {
        return failure.getClass().getName() + ": " + failure.getMessage();
    }

    @FunctionalInterface
    interface Generator {
        TspProblem generate(int n, long seed, GenerationOptions options);
    }
}
