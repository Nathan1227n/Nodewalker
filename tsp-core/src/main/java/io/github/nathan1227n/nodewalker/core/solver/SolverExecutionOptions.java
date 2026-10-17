package io.github.nathan1227n.nodewalker.core.solver;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Opções imutáveis da API controlada. Ausência desativa um limite; zero impede iniciar
 * exploração. Um orçamento K admite K estados; só a tentativa do próximo atinge o limite.
 * Tempo expira em elapsedNanos >= maxElapsedMillis * 1_000_000, de forma cooperativa.
 * Sem limites exige autorização explícita e aprovação da política para o algoritmo/N.
 */
public record SolverExecutionOptions(OptionalLong maxVisitedNodes, OptionalLong maxElapsedMillis,
                                      boolean allowUnlimited, SolverExecutionPolicy policy) {
    public SolverExecutionOptions {
        nonNegative(maxVisitedNodes, "maxVisitedNodes");
        nonNegative(maxElapsedMillis, "maxElapsedMillis");
        Objects.requireNonNull(policy, "A política não pode ser nula.");
        if (maxElapsedMillis.isPresent() && maxElapsedMillis.getAsLong() > Long.MAX_VALUE / 1_000_000) {
            throw new IllegalArgumentException("maxElapsedMillis não é representável em nanossegundos long.");
        }
        if (maxVisitedNodes.isEmpty() && maxElapsedMillis.isEmpty() && !allowUnlimited) {
            throw new IllegalArgumentException("Ative um limite ou autorize explicitamente execução sem limites.");
        }
    }

    public static SolverExecutionOptions nodeLimit(long maxNodes) {
        return new SolverExecutionOptions(OptionalLong.of(maxNodes), OptionalLong.empty(), false,
                SolverExecutionPolicy.demonstration());
    }

    public static SolverExecutionOptions timeLimitMillis(long maxMillis) {
        return new SolverExecutionOptions(OptionalLong.empty(), OptionalLong.of(maxMillis), false,
                SolverExecutionPolicy.demonstration());
    }

    public static SolverExecutionOptions limits(long maxNodes, long maxMillis) {
        return new SolverExecutionOptions(OptionalLong.of(maxNodes), OptionalLong.of(maxMillis), false,
                SolverExecutionPolicy.demonstration());
    }

    public static SolverExecutionOptions unlimited() {
        return new SolverExecutionOptions(OptionalLong.empty(), OptionalLong.empty(), true,
                SolverExecutionPolicy.demonstration());
    }

    public SolverExecutionOptions withPolicy(SolverExecutionPolicy executionPolicy) {
        return new SolverExecutionOptions(maxVisitedNodes, maxElapsedMillis, allowUnlimited, executionPolicy);
    }

    public boolean hasLimits() {
        return maxVisitedNodes.isPresent() || maxElapsedMillis.isPresent();
    }

    private static void nonNegative(OptionalLong value, String name) {
        Objects.requireNonNull(value, name + " não pode ser nulo.");
        if (value.isPresent() && value.getAsLong() < 0) {
            throw new IllegalArgumentException(name + " deve ser não negativo.");
        }
    }
}
