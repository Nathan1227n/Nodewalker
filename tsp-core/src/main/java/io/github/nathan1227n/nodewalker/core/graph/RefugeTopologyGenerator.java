package io.github.nathan1227n.nodewalker.core.graph;

import io.github.nathan1227n.nodewalker.core.generator.GenerationCancellationToken;
import java.util.ArrayList;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** Versioned, bounded generation: shuffled spanning chain and optional distance-two chords. */
public final class RefugeTopologyGenerator {
    public static final String VERSION = "chain-chords-v1";

    private RefugeTopologyGenerator() { }

    public static RefugeTopology connectedSparse(int size, long seed, int extraConnections) {
        return connectedSparse(size, seed, extraConnections, RefugeTopology.DEFAULT_MAX_ESTIMATED_BYTES,
                new GenerationCancellationToken());
    }

    /** Generation cancellation is not a shortest-path solver result. Thread interruption is preserved. */
    public static RefugeTopology connectedSparse(int size, long seed, int extraConnections,
                                                 long maxEstimatedBytes, GenerationCancellationToken token) {
        Objects.requireNonNull(token);
        if (size < 3 || extraConnections < 0 || extraConnections > size - 2) {
            throw new IllegalArgumentException("Require N >= 3 and 0 <= extras <= N-2.");
        }
        long edges = size - 1L + extraConnections;
        RefugeTopology.requireStorage(size, edges, maxEstimatedBytes);
        Runnable checkpoint = () -> {
            if (token.isCancellationRequested() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Refuge topology generation cancelled.");
            }
        };
        checkpoint.run();
        int[] order = new int[size];
        int[] chords = new int[size - 2];
        for (int i = 0; i < size; i++) {
            checkpoint.run();
            order[i] = i;
            if (i < chords.length) chords[i] = i;
        }
        Random random = new Random(seed);
        shuffle(order, random, checkpoint);
        shuffle(chords, random, checkpoint);
        var connections = new ArrayList<RefugeTopology.Edge>(Math.toIntExact(edges));
        for (int i = 1; i < size; i++) {
            checkpoint.run();
            connections.add(new RefugeTopology.Edge(order[i - 1], order[i]));
        }
        for (int i = 0; i < extraConnections; i++) {
            checkpoint.run();
            int position = chords[i];
            connections.add(new RefugeTopology.Edge(order[position], order[position + 2]));
        }
        return RefugeTopology.build(size, connections, RefugeTopology.Kind.SPARSE_CONNECTED,
                OptionalLong.of(seed), maxEstimatedBytes, checkpoint);
    }

    private static void shuffle(int[] values, Random random, Runnable checkpoint) {
        for (int i = values.length - 1; i > 0; i--) {
            checkpoint.run();
            int other = random.nextInt(i + 1);
            int swap = values[i];
            values[i] = values[other];
            values[other] = swap;
        }
    }
}
