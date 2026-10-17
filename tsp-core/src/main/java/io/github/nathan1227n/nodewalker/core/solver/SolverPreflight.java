package io.github.nathan1227n.nodewalker.core.solver;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Relatório sem UI e sem geração. Não promete duração nem estima efeito das podas.
 * theoreticalRoutes é exato somente quando exactSearchSpace é true; caso contrário
 * é um limite inferior demonstrado. O cálculo usa no máximo 256 bits de resultado,
 * independentemente de N: essa proteção de relatório não é um N máximo do solver.
 */
public record SolverPreflight(SolverAlgorithm algorithm, int cityCount, int originIndex,
                              BigInteger theoreticalRoutes, boolean exactSearchSpace,
                              SolverExecutionOptions options, boolean outsideRecommendedRange,
                              boolean requiresConfirmation, boolean executionAllowed, List<String> warnings) {
    private static final int SPACE_BITS = 256;

    public SolverPreflight {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(theoreticalRoutes);
        Objects.requireNonNull(options);
        warnings = List.copyOf(warnings);
        if (cityCount < 3 || originIndex != 0 || theoreticalRoutes.signum() <= 0) {
            throw new IllegalArgumentException("Preflight exige N >= 3, origem 0 e espaço positivo.");
        }
    }

    /** Analisa somente metadados: não exige construir uma instância potencialmente enorme. */
    public static SolverPreflight analyze(SolverAlgorithm algorithm, int cityCount, SolverExecutionOptions options) {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(options);
        if (cityCount < 3) {
            throw new IllegalArgumentException("Uma instância TSP deve ter N >= 3.");
        }
        BigInteger space = BigInteger.ONE;
        boolean exact = true;
        for (int factor = 2; factor < cityCount; factor++) {
            BigInteger next = space.multiply(BigInteger.valueOf(factor));
            if (next.bitLength() > SPACE_BITS) {
                exact = false;
                break;
            }
            space = next;
        }
        boolean outside = cityCount > options.policy().recommendedMaxCities(algorithm);
        boolean allowed = options.hasLimits() || (options.allowUnlimited() && !outside);
        String warning = "O espaço (N-1)! cresce fatorialmente; poda não garante viabilidade para N grande.";
        List<String> warnings = exact ? List.of(warning) : List.of(warning,
                "Espaço exato não calculado: theoreticalRoutes é somente um limite inferior demonstrável.");
        return new SolverPreflight(algorithm, cityCount, 0, space, exact, options, outside, outside, allowed, warnings);
    }

    /** Recusa operacional antes da busca; não fabrica um TspResult para execução não iniciada. */
    public void requireAllowed() {
        if (!executionAllowed) {
            throw new IllegalArgumentException("Preflight recusado: N=" + cityCount + ", solver=" + algorithm
                    + ", fora da faixa recomendada; pelo menos um limite de nós ou tempo é obrigatório."
                    + " Espaço (N-1)! " + (exactSearchSpace ? "= " : ">= ") + theoreticalRoutes
                    + (exactSearchSpace ? "." : " (espaço exato não calculado)."));
        }
    }
}
