package io.github.nathan1227n.nodewalker.core.generator;

import java.math.BigInteger;

/** Solicitação rejeitada antes de grandes alocações por exceder o modelo de orçamento de memória. */
public final class GenerationMemoryLimitExceededException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final BigInteger estimatedBytes;
    private final long maxEstimatedBytes;

    GenerationMemoryLimitExceededException(BigInteger estimatedBytes, long maxEstimatedBytes) {
        super("A memória de pico estimada (" + estimatedBytes + " bytes) excede o orçamento de "
                + maxEstimatedBytes + " bytes.");
        this.estimatedBytes = estimatedBytes;
        this.maxEstimatedBytes = maxEstimatedBytes;
    }

    public BigInteger estimatedBytes() {
        return estimatedBytes;
    }

    public long maxEstimatedBytes() {
        return maxEstimatedBytes;
    }
}
