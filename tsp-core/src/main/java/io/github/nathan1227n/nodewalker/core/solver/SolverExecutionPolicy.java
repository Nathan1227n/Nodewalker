package io.github.nathan1227n.nodewalker.core.solver;

import java.util.Objects;

/**
 * Faixas operacionais configuráveis por algoritmo, não limites matemáticos de N.
 * A evidência identifica ambiente, orçamento e benchmark que sustentam a recomendação.
 * Fora da faixa, a API controlada exige ao menos um limite mesmo com autorização sem limites.
 */
public record SolverExecutionPolicy(int bruteForceRecommendedMaxCities,
                                    int backtrackingRecommendedMaxCities, String evidence) {
    public SolverExecutionPolicy {
        if (bruteForceRecommendedMaxCities < 3 || backtrackingRecommendedMaxCities < 3) {
            throw new IllegalArgumentException("As faixas recomendadas devem incluir N >= 3.");
        }
        Objects.requireNonNull(evidence, "A evidência da política não pode ser nula.");
        if (evidence.isBlank()) {
            throw new IllegalArgumentException("A política exige identificação da evidência de benchmark.");
        }
    }

    /** Perfil da demonstração local; revalidar para outro ambiente/dataset. */
    public static SolverExecutionPolicy demonstration() {
        return new SolverExecutionPolicy(10, 10, "docs/CONTROLE_SOLVERS.md — benchmark G2, 17/10/2026");
    }

    public int recommendedMaxCities(SolverAlgorithm algorithm) {
        return switch (Objects.requireNonNull(algorithm)) {
            case BRUTE_FORCE -> bruteForceRecommendedMaxCities;
            case BACKTRACKING -> backtrackingRecommendedMaxCities;
        };
    }
}
