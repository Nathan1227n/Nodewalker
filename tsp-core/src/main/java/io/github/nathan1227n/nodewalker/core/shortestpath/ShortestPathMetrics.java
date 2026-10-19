package io.github.nathan1227n.nodewalker.core.shortestpath;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Actual per-run work. A processed vertex has a final distance, even if its outgoing scan is interrupted.
 * Examined edges count adjacency entries; relaxation attempts exclude already fixed neighbors.
 * Successful relaxations strictly improve labels. Time excludes preflight and result validation/copying.
 */
public record ShortestPathMetrics(OptionalLong elapsedNanos, long processedVertices, long examinedEdges,
                                  long relaxationAttempts, long successfulRelaxations, long peakQueueSize) {
    public ShortestPathMetrics {
        Objects.requireNonNull(elapsedNanos);
        if ((elapsedNanos.isPresent() && elapsedNanos.getAsLong() < 0) || processedVertices < 0
                || examinedEdges < 0 || relaxationAttempts < 0 || successfulRelaxations < 0 || peakQueueSize < 0
                || successfulRelaxations > relaxationAttempts || relaxationAttempts > examinedEdges) {
            throw new IllegalArgumentException("Invalid shortest-path metrics.");
        }
    }
}
