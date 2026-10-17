package io.github.nathan1227n.nodewalker.core.solver;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pedido thread-safe, persistente e idempotente de cancelamento de busca.
 * Não controla geração. Uma execução independente deve usar um novo token; não há reset.
 */
public final class SolverCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancellationRequested() {
        return cancelled.get();
    }
}
