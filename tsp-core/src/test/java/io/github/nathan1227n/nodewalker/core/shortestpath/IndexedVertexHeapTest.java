package io.github.nathan1227n.nodewalker.core.shortestpath;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IndexedVertexHeapTest {
    @Test
    void repeatedDecreasesAndRemovalsAgreeWithIndependentLinearMinimum() {
        var keys = new BigInteger[100];
        var heap = new IndexedVertexHeap(keys);
        var expected = new HashMap<Integer, BigInteger>();
        var random = new Random(657);
        for (int step = 0; step < 2000; step++) {
            int vertex = random.nextInt(keys.length);
            BigInteger candidate = expected.getOrDefault(vertex, BigInteger.valueOf(Long.MAX_VALUE))
                    .subtract(BigInteger.valueOf(random.nextInt(100)));
            keys[vertex] = candidate;
            expected.put(vertex, candidate);
            heap.insertOrDecrease(vertex);
            assertEquals(expected.size(), heap.size());
            if (step % 3 == 0) removeMinimum(heap, expected);
        }
        while (!expected.isEmpty()) removeMinimum(heap, expected);
        assertEquals(0, heap.size());
        assertThrows(IllegalStateException.class, heap::removeMin);
    }

    private static void removeMinimum(IndexedVertexHeap heap, HashMap<Integer, BigInteger> expected) {
        int minimum = expected.keySet().stream()
                .min(Comparator.<Integer, BigInteger>comparing(expected::get).thenComparingInt(v -> v)).orElseThrow();
        assertEquals(minimum, heap.removeMin());
        expected.remove(minimum);
    }
}
