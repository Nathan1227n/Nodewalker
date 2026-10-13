package io.github.nathan1227n.nodewalker.core.generator;

import java.util.Objects;

/**
 * Orçamentos imutáveis e sinal mutável explícito de uma geração controlada.
 * Não existem limites implícitos: ambos os orçamentos são escolhidos pelo chamador.
 * O orçamento de bytes rege o modelo de {@link TspGenerator#estimatePeakMemoryBytes(int)},
 * não reserva heap nem garante ausência de OutOfMemoryError.
 *
 * @param maxCandidates máximo total de candidatos x/y/z, incluindo rejeições
 * @param maxEstimatedBytes máximo estimado para o pico de memória da geração
 * @param cancellationToken sinal thread-safe, inicialmente não cancelado para uma nova operação
 */
public record GenerationOptions(long maxCandidates, long maxEstimatedBytes,
                                GenerationCancellationToken cancellationToken) {
    /**
     * @throws IllegalArgumentException se algum orçamento não for positivo
     * @throws NullPointerException se o token for nulo
     */
    public GenerationOptions {
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates deve ser positivo: " + maxCandidates + ".");
        }
        if (maxEstimatedBytes <= 0) {
            throw new IllegalArgumentException("maxEstimatedBytes deve ser positivo: " + maxEstimatedBytes + ".");
        }
        Objects.requireNonNull(cancellationToken, "O token de cancelamento não pode ser nulo.");
    }

    /** Cria opções com os orçamentos explícitos e um token novo, não cancelado. */
    public GenerationOptions(long maxCandidates, long maxEstimatedBytes) {
        this(maxCandidates, maxEstimatedBytes, new GenerationCancellationToken());
    }
}
