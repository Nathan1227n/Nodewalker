package io.github.nathan1227n.nodewalker.core.generator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sinal de cancelamento de geração, thread-safe e independente do controle dos solvers.
 * O pedido é persistente e idempotente; uma nova operação independente deve usar
 * um novo token. Não há reset, estado global ou dependência gráfica.
 */
public final class GenerationCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** Solicita cancelamento cooperativo para todas as gerações que compartilham este token. */
    public void cancel() {
        cancelled.set(true);
    }

    /** @return se o cancelamento já foi solicitado */
    public boolean isCancellationRequested() {
        return cancelled.get();
    }
}
