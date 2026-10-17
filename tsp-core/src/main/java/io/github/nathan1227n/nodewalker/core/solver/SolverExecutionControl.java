package io.github.nathan1227n.nodewalker.core.solver;

/** Controle por chamada; somente o token é compartilhável entre threads. */
final class SolverExecutionControl {
    enum Phase { PREPARATION, BEFORE_NODE, BEFORE_DESCENT, EDGE, ITERATION, FINALIZATION }

    @FunctionalInterface
    interface Observer {
        void onCheckpoint(Phase phase, long visitedNodes, long evaluatedRoutes);
    }

    private final SolverRunMetrics metrics;
    private final SolverExecutionOptions options;
    private final SolverCancellationToken token;
    private final Observer observer;

    SolverExecutionControl(SolverRunMetrics metrics, SolverExecutionOptions options,
                           SolverCancellationToken token, Observer observer) {
        this.metrics = metrics;
        this.options = options;
        this.token = token;
        this.observer = observer;
    }

    void checkpoint(Phase phase) {
        if (options == null) {
            return; // Contrato legado: nenhum controle, interrupção ou leitura extra de relógio.
        }
        if (observer != null) {
            observer.onCheckpoint(phase, metrics.visitedNodes(), metrics.evaluatedRoutes());
        }
        boolean cancelled = token.isCancellationRequested();
        boolean interrupted = Thread.currentThread().isInterrupted();
        // Falha real na leitura é propagada antes de escolher um terminal de controle.
        boolean timedOut = options.maxElapsedMillis().isPresent()
                && metrics.elapsedAtCheckpoint() >= options.maxElapsedMillis().getAsLong() * 1_000_000;
        boolean needsNode = phase == Phase.BEFORE_NODE || phase == Phase.BEFORE_DESCENT;
        boolean nodesExhausted = needsNode && options.maxVisitedNodes().isPresent()
                && metrics.visitedNodes() >= options.maxVisitedNodes().getAsLong();
        if (cancelled || interrupted || timedOut || nodesExhausted) {
            String reason = "Interrupção por " + (cancelled || interrupted ? "cancelamento" : timedOut ? "tempo" : "nós")
                    + ". Condições observadas:" + (cancelled ? " token cancelado;" : "")
                    + (interrupted ? " thread interrompida (sinal preservado);" : "")
                    + (timedOut ? " tempo >= " + options.maxElapsedMillis().getAsLong() + " ms;" : "")
                    + (nodesExhausted ? " orçamento de " + options.maxVisitedNodes().getAsLong() + " nós esgotado;" : "")
                    + " fase=" + phase + ", nós=" + metrics.visitedNodes() + ".";
            throw new Stopped(cancelled || interrupted ? SolverStatus.CANCELADO : SolverStatus.LIMITE_ATINGIDO, reason);
        }
    }

    /** Sinal interno distinto de falhas reais; não é capturado como RuntimeException genérica. */
    static final class Stopped extends RuntimeException {
        private final SolverStatus status;

        private Stopped(SolverStatus status, String reason) {
            super(reason, null, false, false);
            this.status = status;
        }

        SolverStatus status() {
            return status;
        }
    }
}
