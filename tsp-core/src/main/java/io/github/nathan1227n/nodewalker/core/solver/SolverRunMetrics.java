package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.SearchSpace;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/** Instrumentação local à chamada, sem API pública de limites ou execução. */
final class SolverRunMetrics {
    private final LongSupplier clock;
    private long evaluatedRoutes;
    private long visitedNodes;
    private long start;
    private boolean started;
    private boolean stopped;
    private OptionalLong elapsedNanos = OptionalLong.empty();

    SolverRunMetrics(LongSupplier clock) {
        this(clock, 0, 0);
    }

    /** Estado inicial explícito para testar a capacidade dos contadores em tempo constante. */
    SolverRunMetrics(LongSupplier clock, long evaluatedRoutes, long visitedNodes) {
        this.clock = Objects.requireNonNull(clock);
        if (evaluatedRoutes < 0 || visitedNodes < 0) {
            throw new IllegalArgumentException("Contadores observados devem ser não negativos.");
        }
        this.evaluatedRoutes = evaluatedRoutes;
        this.visitedNodes = visitedNodes;
    }

    void start() {
        start = clock.getAsLong();
        started = true;
    }

    void visit() {
        visitedNodes = Math.incrementExact(visitedNodes);
    }

    long visitedNodes() {
        return visitedNodes;
    }

    long evaluatedRoutes() {
        return evaluatedRoutes;
    }

    long elapsedAtCheckpoint() {
        if (stopped) {
            return elapsedNanos.orElseThrow();
        }
        return durationNow();
    }

    private long durationNow() {
        long duration = clock.getAsLong() - start;
        if (duration < 0) {
            throw new IllegalStateException("Duração do solver negativa ou não representável em long.");
        }
        return duration;
    }

    long nextEvaluationCount() {
        return Math.incrementExact(evaluatedRoutes);
    }

    /** Publica uma reserva obtida antes de calcular o ciclo, somente após o cálculo completo. */
    void completeEvaluation(long reservedCount) {
        evaluatedRoutes = reservedCount;
    }

    void requireCompleteCoverage(int cityCount, BigInteger discardedRoutes) {
        if (!BigInteger.valueOf(evaluatedRoutes).add(discardedRoutes)
                .equals(SearchSpace.completeRoutes(cityCount))) {
            throw new IllegalStateException("Avaliadas + descartadas não cobrem todas as (N-1)! rotas.");
        }
    }

    void stop() {
        if (stopped || !started) {
            return;
        }
        // Marca antes de ler: falha no relógio não deve causar novas tentativas ao reportar ERRO.
        stopped = true;
        // Subtração intencionalmente modular: a origem pode ser negativa e atravessar o limite.
        elapsedNanos = OptionalLong.of(durationNow());
    }

    SolverMetrics snapshot(Optional<BigInteger> discardedRoutes) {
        return new SolverMetrics(elapsedNanos, OptionalLong.of(evaluatedRoutes),
                OptionalLong.of(visitedNodes), discardedRoutes);
    }

    TspResult errorResult(String solverName, TspProblem problem, TspRoute incumbent, long cost,
                          PruningHistogram pruning, RuntimeException failure) {
        return interruptedResult(problem, incumbent, cost, pruning, SolverStatus.ERRO,
                "Falha no " + solverName + ": " + diagnostic(failure));
    }

    TspResult interruptedResult(TspProblem problem, TspRoute incumbent, long cost,
                                PruningHistogram pruning, SolverStatus status, String reason) {
        Optional<BigInteger> discarded = Optional.empty();
        try {
            discarded = Optional.of(pruning == null ? BigInteger.ZERO : pruning.discardedRoutes());
        } catch (RuntimeException metricsFailure) {
            reason += "; falha ao consolidar descartes: " + diagnostic(metricsFailure);
        }
        try {
            stop();
        } catch (RuntimeException timingFailure) {
            reason += "; falha ao medir duração: " + diagnostic(timingFailure);
        }
        SolverMetrics metrics = snapshot(discarded);
        if (incumbent != null) {
            try {
                return TspResult.interrupted(problem, status, Optional.of(incumbent),
                        OptionalLong.of(cost), metrics, reason);
            } catch (RuntimeException validationFailure) {
                reason += "; falha ao validar incumbente: " + diagnostic(validationFailure);
            }
        }
        return TspResult.interrupted(problem, status, Optional.empty(), OptionalLong.empty(), metrics, reason);
    }

    private static String diagnostic(RuntimeException failure) {
        return failure.getClass().getName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    }
}
