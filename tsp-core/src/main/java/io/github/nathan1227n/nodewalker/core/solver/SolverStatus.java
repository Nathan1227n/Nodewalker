package io.github.nathan1227n.nodewalker.core.solver;

/** Estados do solver (DOC 02 §8.1), separados dos futuros estados de playback da UI. */
public enum SolverStatus {
    PRONTO,
    EXECUTANDO,
    CANCELANDO,
    /** Busca concluída corretamente; a garantia global de ótimo cabe ao solver. */
    CONCLUIDO,
    CANCELADO,
    LIMITE_ATINGIDO,
    ERRO;

    /** @return se o estado pode representar um resultado final de execução */
    public boolean isTerminal() {
        return switch (this) {
            case CONCLUIDO, CANCELADO, LIMITE_ATINGIDO, ERRO -> true;
            default -> false;
        };
    }
}
