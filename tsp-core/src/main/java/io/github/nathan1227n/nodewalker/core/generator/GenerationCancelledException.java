package io.github.nathan1227n.nodewalker.core.generator;

import java.util.concurrent.CancellationException;

/** Geração cancelada cooperativamente, sem instância parcial ou resultado de solver. */
public final class GenerationCancelledException extends CancellationException {
    private static final long serialVersionUID = 1L;

    private final long attemptedCandidates;
    private final int acceptedCities;

    GenerationCancelledException(long attemptedCandidates, int acceptedCities) {
        super("Geração cancelada após " + attemptedCandidates + " candidatos sorteados e "
                + acceptedCities + " cidades aceitas.");
        this.attemptedCandidates = attemptedCandidates;
        this.acceptedCities = acceptedCities;
    }

    /** @return trios completamente sorteados; um candidato pode ainda não ter sido classificado */
    public long attemptedCandidates() {
        return attemptedCandidates;
    }

    public int acceptedCities() {
        return acceptedCities;
    }
}
