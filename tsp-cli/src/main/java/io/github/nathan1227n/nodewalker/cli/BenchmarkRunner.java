package io.github.nathan1227n.nodewalker.cli;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.solver.TspSolver;
import io.github.nathan1227n.nodewalker.core.solver.SolverStatus;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Mede somente solve na mesma instância; não gera cidades, imprime ou registra eventos. */
final class BenchmarkRunner {
    private final LongSupplier clock;

    BenchmarkRunner(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    Report run(TspProblem problem, TspSolver solver, int warmup, int runs) {
        Objects.requireNonNull(problem);
        Objects.requireNonNull(solver);
        if (warmup < 0 || runs < 1) {
            throw new IllegalArgumentException("Aquecimentos devem ser >= 0 e medições >= 1.");
        }
        TspResult reference = null;
        for (int i = 0; i < warmup; i++) {
            TspResult result;
            try {
                result = solver.solve(problem);
            } catch (RuntimeException cause) {
                throw exception("Aquecimento", i + 1, cause);
            }
            reference = consistent(reference, result, "Aquecimento", i + 1);
        }

        long[] durations = new long[runs];
        TspResult firstMeasured = null;
        for (int i = 0; i < runs; i++) {
            TspResult result;
            long elapsed;
            try {
                long start = clock.getAsLong();
                result = solver.solve(problem);
                long end = clock.getAsLong();
                elapsed = end - start;
            } catch (RuntimeException cause) {
                throw exception("Medição", i + 1, cause);
            }
            reference = consistent(reference, result, "Medição", i + 1);
            if (elapsed < 0) {
                throw new Failure("Medição " + (i + 1) + ": duração negativa observada.", result, null);
            }
            durations[i] = elapsed;
            if (firstMeasured == null) {
                firstMeasured = result;
            }
        }
        return new Report(firstMeasured, durations, Median.of(durations));
    }

    private static TspResult consistent(TspResult reference, TspResult result, String phase, int iteration) {
        String location = phase + " " + iteration + ": ";
        if (result == null) {
            throw new Failure(location + "o solver retornou null.", null, null);
        }
        if (result.status() != SolverStatus.CONCLUIDO) {
            throw new Failure(location + "o solver terminou em " + result.status()
                    + ". " + result.reason().orElse("Diagnóstico não informado."), result, null);
        }
        if (reference != null && (reference.status() != result.status()
                || !reference.route().equals(result.route()) || !reference.cost().equals(result.cost()))) {
            throw new Failure(location + "resultado inconsistente. Esperado estado=" + reference.status()
                    + ", rota=" + reference.route().orElseThrow().indices() + ", custo=" + reference.cost().orElseThrow()
                    + "; obtido estado=" + result.status() + ", rota=" + result.route().orElseThrow().indices()
                    + ", custo=" + result.cost().orElseThrow() + ".", result, null);
        }
        return reference == null ? result : reference;
    }

    private static Failure exception(String phase, int iteration, RuntimeException cause) {
        return new Failure(phase + " " + iteration + ": " + cause.getClass().getName()
                + ": " + cause.getMessage(), null, cause);
    }

    record Report(TspResult result, long[] durationsNanos, BigDecimal medianNanos) {
        Report {
            durationsNanos = durationsNanos.clone();
        }

        @Override
        public long[] durationsNanos() {
            return durationsNanos.clone();
        }
    }

    static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final TspResult result;

        private Failure(String message, TspResult result, RuntimeException cause) {
            super(message, cause);
            this.result = result;
        }

        TspResult result() {
            return result;
        }
    }
}
