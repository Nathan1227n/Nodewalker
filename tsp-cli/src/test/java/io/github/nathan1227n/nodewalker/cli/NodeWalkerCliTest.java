package io.github.nathan1227n.nodewalker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.solver.BruteForceSolver;
import io.github.nathan1227n.nodewalker.core.solver.SolverMetrics;
import io.github.nathan1227n.nodewalker.core.solver.SolverStatus;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.solver.TspSolver;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class NodeWalkerCliTest {
    @Test
    void generatesOnceAndSeparatesGenerationWarmupAndMeasurementsOnTheSameProblem() {
        AtomicInteger generated = new AtomicInteger();
        AtomicInteger solved = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        AtomicLong time = new AtomicLong();
        AtomicReference<TspProblem> instance = new AtomicReference<>();
        NodeWalkerCli cli = new NodeWalkerCli((n, seed, options) -> {
            generated.incrementAndGet();
            assertEquals(100_000L, options.maxCandidates());
            assertEquals(67_108_864L, options.maxEstimatedBytes());
            assertFalse(options.cancellationToken().isCancellationRequested());
            time.addAndGet(1_000_000);
            TspProblem problem = TspGenerator.generate(n, seed, options);
            instance.set(problem);
            return problem;
        }, problem -> {
            assertSame(instance.get(), problem);
            solved.incrementAndGet();
            time.addAndGet(5);
            return new BruteForceSolver().solve(problem);
        }, () -> {
            ticks.incrementAndGet();
            return time.get();
        });

        Output output = run(cli, CliOptionsTest.args());

        assertEquals(0, output.exit());
        assertEquals("", output.err());
        assertEquals(1, generated.get());
        assertEquals(8, solved.get());
        assertEquals(12, ticks.get());
        assertTrue(output.out().contains("Espaço teórico (N-1)!: 6"));
        assertTrue(output.out().contains("medições): 48"));
        assertTrue(output.out().contains("Tempo de geração (ns): 1000000"));
        assertTrue(output.out().contains("na ordem de execução: [5, 5, 5, 5, 5]"));
        assertTrue(output.out().contains("Mediana do solver (ns): 5"));
        assertTrue(output.out().contains("Mediana do solver (ms): 0.000005"));
        assertTrue(output.out().contains("Seed da instância: 657"));
        assertTrue(output.out().contains("Rota: 0 -> 2 -> 1 -> 3 -> 0"));
        assertTrue(output.out().contains("Custo: 2799"));
        assertTrue(output.out().contains("completeRoutesEvaluated: 6"));
        assertTrue(output.out().contains("elapsedNanos: não informado"));
        assertTrue(output.out().contains("visitedNodes: não informado"));
        assertTrue(output.out().contains("discardedRoutes: não informado"));
        assertTrue(output.out().contains("primeira execução medida"));
    }

    @Test
    void printsHelpWithoutGeneratingSolvingOrReadingTheClock() {
        Output output = run(unexpectedWork(), "--help");

        assertEquals(0, output.exit());
        assertTrue(output.out().contains("Uso: java -jar"));
        assertTrue(output.out().contains("padrão: 10000000"));
        assertEquals("", output.err());
    }

    @ParameterizedTest
    @MethodSource("rejectedRequests")
    void rejectsArgumentsOrPreflightBeforeAnyGenerationOrSolverCall(String[] args, String message) {
        Output output = run(unexpectedWork(), args);

        assertEquals(2, output.exit());
        assertTrue(output.err().contains(message), output.err());
        assertFalse(output.out().contains("Tempo de geração"));
        assertFalse(output.out().contains("Estado do solver"));
        assertFalse(output.err().contains("Estado do solver"));
    }

    @Test
    void handlesRealCandidateExhaustionAsAGenerationFailureWithoutSolverStatus() {
        Output output = run(new NodeWalkerCli(TspGenerator::generate, ignored -> {
            throw new AssertionError("Solver não deve iniciar");
        }, System::nanoTime), CliOptionsTest.args("--max-candidates", "4"));

        assertEquals(1, output.exit());
        assertTrue(output.err().contains("Falha na geração"));
        assertTrue(output.err().contains("GenerationLimitExceededException"));
        assertTrue(output.err().contains("3 cidades aceitas"));
        assertFalse(output.err().contains("Estado do solver"));
        assertFalse(output.out().contains("Mediana"));
    }

    @Test
    void handlesGenerationCancellationUsingTheControlledApi() {
        Output output = run(new NodeWalkerCli((n, seed, options) -> {
            options.cancellationToken().cancel();
            return TspGenerator.generate(n, seed, options);
        }, ignored -> { throw new AssertionError("Solver não deve iniciar"); }, System::nanoTime), CliOptionsTest.args());

        assertEquals(130, output.exit());
        assertTrue(output.err().contains("Geração cancelada"));
        assertTrue(output.err().contains("GenerationCancelledException"));
        assertFalse(output.err().contains("Estado do solver"));
    }

    @Test
    void preservesThreadInterruptionWhenGenerationObservesCancellation() {
        try {
            Thread.currentThread().interrupt();
            Output output = run(new NodeWalkerCli(TspGenerator::generate,
                    ignored -> { throw new AssertionError("Solver não deve iniciar"); }, System::nanoTime), CliOptionsTest.args());

            assertEquals(130, output.exit());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void preservesUnexpectedGenerationDiagnosticsWithoutCreatingASolverResult() {
        Output output = run(new NodeWalkerCli((n, seed, options) -> {
            throw new IllegalStateException("Falha real de geração");
        }, ignored -> { throw new AssertionError("Solver não deve iniciar"); }, System::nanoTime), CliOptionsTest.args());

        assertEquals(1, output.exit());
        assertTrue(output.err().contains("IllegalStateException: Falha real de geração"));
        assertFalse(output.err().contains("Estado do solver"));
    }

    @ParameterizedTest
    @EnumSource(value = SolverStatus.class, names = {"ERRO", "CANCELADO", "LIMITE_ATINGIDO"})
    void printsActualInterruptedResultAndIncumbentWithoutClaimingAnOptimalBenchmark(SolverStatus status) {
        TspProblem problem = TestProblems.triangle();
        TspResult interrupted = TspResult.interrupted(problem, status,
                Optional.of(new TspRoute(List.of(0, 1, 2, 0))), OptionalLong.of(45), SolverMetrics.empty(), "Diagnóstico original");
        Output output = run(new NodeWalkerCli((n, seed, options) -> problem,
                ignored -> interrupted, System::nanoTime), triangleArgs());

        assertEquals(1, output.exit());
        assertTrue(output.err().contains("Benchmark: ERRO"));
        assertTrue(output.err().contains("Estado do solver: " + status));
        assertTrue(output.err().contains("Rota incumbente (sem ótimo comprovado): 0 -> 1 -> 2 -> 0"));
        assertTrue(output.err().contains("Custo: 45"));
        assertTrue(output.err().contains("Diagnóstico original"));
        assertTrue(output.err().contains("Mediana do solver (ns): não calculada"));
        assertFalse(output.out().contains("Durações do solver"));
    }

    @Test
    void reportsActualCostOverflowWithoutPublishingOptimalTimes() {
        TspProblem overflowing = TestProblems.problem(new long[][] {
                {0, Long.MAX_VALUE, 1}, {Long.MAX_VALUE, 0, 0}, {1, 0, 0}
        });
        Output output = run(new NodeWalkerCli((n, seed, options) -> overflowing,
                new BruteForceSolver(), System::nanoTime), triangleArgs());

        assertEquals(1, output.exit());
        assertTrue(output.err().contains("Estado do solver: ERRO"));
        assertTrue(output.err().contains("Long.MAX_VALUE"));
        assertTrue(output.err().contains("completeRoutesEvaluated: 0"));
        assertTrue(output.err().contains("Custo: não informado"));
        assertTrue(output.err().contains("não calculada"));
        assertFalse(output.out().contains("Benchmark: CONCLUIDO"));
    }

    @Test
    void reportsSolverExceptionsWithoutMaskingTheirClassOrMessage() {
        Output output = run(new NodeWalkerCli(TspGenerator::generate, ignored -> {
            throw new IllegalStateException("Erro real do solver");
        }, System::nanoTime), CliOptionsTest.args());

        assertEquals(1, output.exit());
        assertTrue(output.err().contains("Aquecimento 1: java.lang.IllegalStateException: Erro real do solver"));
        assertTrue(output.err().contains("não calculada"));
        assertFalse(output.out().contains("Durações do solver"));
    }

    @Test
    void rejectsDivergenceAfterAnEarlierValidMeasuredRunWithoutAPartialMedian() {
        TspProblem problem = TestProblems.fourCities(true);
        AtomicInteger calls = new AtomicInteger();
        TspSolver solver = ignored -> TestProblems.completed(problem,
                calls.getAndIncrement() == 0 ? List.of(0, 1, 2, 3, 0) : List.of(0, 1, 3, 2, 0), SolverMetrics.empty());
        Output output = run(new NodeWalkerCli((n, seed, options) -> problem, solver, System::nanoTime),
                CliOptionsTest.args("--warmup", "0", "--runs", "3"));

        assertEquals(1, output.exit());
        assertEquals(2, calls.get());
        assertTrue(output.err().contains("Medição 2: resultado inconsistente"));
        assertTrue(output.err().contains("não calculada"));
        assertFalse(output.out().contains("Durações do solver"));
    }

    @Test
    void distinguishesMeasuredZeroFromAbsentMetricsAndKeepsExternalTimingSeparate() {
        TspProblem problem = TestProblems.triangle();
        SolverMetrics metrics = new SolverMetrics(OptionalLong.of(0), OptionalLong.empty(),
                OptionalLong.of(0), Optional.of(BigInteger.ZERO));
        AtomicInteger clock = new AtomicInteger();
        Output output = run(new NodeWalkerCli((n, seed, options) -> problem,
                ignored -> TestProblems.completed(problem, List.of(0, 1, 2, 0), metrics),
                () -> clock.getAndAdd(10)), triangleArgs());

        assertEquals(0, output.exit());
        assertTrue(output.out().contains("completeRoutesEvaluated: não informado"));
        assertTrue(output.out().contains("elapsedNanos: 0"));
        assertTrue(output.out().contains("visitedNodes: 0"));
        assertTrue(output.out().contains("discardedRoutes: 0"));
        assertTrue(output.out().contains("Mediana do solver (ns): 10"));
    }

    @Test
    void moduleClasspathDoesNotContainJavaFx() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("javafx.application.Application"));
    }

    private static Stream<Arguments> rejectedRequests() {
        return Stream.of(
                Arguments.of(new String[0], "Argumentos inválidos"),
                Arguments.of(new String[] {"--n", "4", "--seed", "0", "--solver", "backtracking"}, "indisponível"),
                Arguments.of(CliOptionsTest.args("--max-total-routes", "47"), "Preflight recusado"),
                Arguments.of(CliOptionsTest.args("--max-candidates", "3"), "--max-candidates"),
                Arguments.of(CliOptionsTest.args("--max-estimated-bytes", "2303"), "--max-estimated-bytes"),
                Arguments.of(new String[] {"--n", "11", "--seed", "0", "--solver", "brute-force"}, "29030400"),
                Arguments.of(new String[] {"--n", "2147483647", "--seed", "0", "--solver", "brute-force"}, "Preflight recusado"));
    }

    private static NodeWalkerCli unexpectedWork() {
        return new NodeWalkerCli((n, seed, options) -> { throw new AssertionError("Geração indevida"); },
                ignored -> { throw new AssertionError("Solver indevido"); },
                () -> { throw new AssertionError("Relógio indevido"); });
    }

    private static String[] triangleArgs() {
        return new String[] {"--n", "3", "--seed", "0", "--solver", "brute-force", "--warmup", "0", "--runs", "1"};
    }

    private static Output run(NodeWalkerCli cli, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exit = cli.run(args, new PrintWriter(out), new PrintWriter(err));
        return new Output(exit, out.toString(), err.toString());
    }

    private record Output(int exit, String out, String err) {
    }
}
