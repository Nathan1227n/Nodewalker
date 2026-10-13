package io.github.nathan1227n.nodewalker.core.generator;

/** Orçamento de candidatos insuficiente, detectado no preflight ou antes de um novo sorteio. */
public final class GenerationLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final long maxCandidates;
    private final long attemptedCandidates;
    private final int acceptedCities;

    GenerationLimitExceededException(int requestedCities, long maxCandidates,
                                     long attemptedCandidates, int acceptedCities) {
        super("O orçamento de " + maxCandidates + " candidatos é insuficiente para " + requestedCities
                + " cidades: " + attemptedCandidates + " sorteados e " + acceptedCities + " cidades aceitas.");
        this.maxCandidates = maxCandidates;
        this.attemptedCandidates = attemptedCandidates;
        this.acceptedCities = acceptedCities;
    }

    public long maxCandidates() {
        return maxCandidates;
    }

    /** @return trios completamente sorteados; zero em uma rejeição preliminar */
    public long attemptedCandidates() {
        return attemptedCandidates;
    }

    public int acceptedCities() {
        return acceptedCities;
    }
}
