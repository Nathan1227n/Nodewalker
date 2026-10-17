package io.github.nathan1227n.nodewalker.core.solver;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Snapshot imutável de métricas reportadas, inclusive em execuções interrompidas.
 * Ausência significa não reportado, não zero. Os solvers medem sua própria duração;
 * a medição externa da CLI é independente e não preenche este snapshot.
 * Contadores observados usam long sob limites operacionais representáveis;
 * folhas descartadas usam BigInteger. O espaço teórico é fornecido por SearchSpace.
 *
 * @param elapsedNanos duração interna: preparação, busca e consolidação, antes da fábrica de TspResult
 * @param completeRoutesEvaluated ciclos calculados com sucesso, incluindo retorno à origem
 * @param visitedNodes no backtracking, entradas na recursão, incluindo raiz e podas;
 *                     na força bruta, permutações cujo processamento começou, inclusive falhas
 * @param discardedRoutes folhas completas eliminadas por poda, não quantidade de chamadas de poda
 */
public record SolverMetrics(OptionalLong elapsedNanos, OptionalLong completeRoutesEvaluated,
                            OptionalLong visitedNodes, Optional<BigInteger> discardedRoutes) {
    /**
     * @throws NullPointerException se algum campo opcional for nulo
     * @throws IllegalArgumentException se alguma métrica presente for negativa
     */
    public SolverMetrics {
        requireNonNegative(elapsedNanos, "elapsedNanos");
        requireNonNegative(completeRoutesEvaluated, "completeRoutesEvaluated");
        requireNonNegative(visitedNodes, "visitedNodes");
        Objects.requireNonNull(discardedRoutes, "discardedRoutes não pode ser nulo.");
        if (discardedRoutes.isPresent() && discardedRoutes.get().signum() < 0) {
            throw new IllegalArgumentException("discardedRoutes deve ser não negativo.");
        }
    }

    /** @return snapshot em que nenhuma métrica foi reportada */
    public static SolverMetrics empty() {
        return new SolverMetrics(OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), Optional.empty());
    }

    private static void requireNonNegative(OptionalLong value, String name) {
        Objects.requireNonNull(value, name + " não pode ser nulo.");
        if (value.isPresent() && value.getAsLong() < 0) {
            throw new IllegalArgumentException(name + " deve ser não negativo.");
        }
    }
}
