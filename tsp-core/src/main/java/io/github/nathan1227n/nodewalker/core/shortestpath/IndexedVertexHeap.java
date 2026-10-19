package io.github.nathan1227n.nodewalker.core.shortestpath;

import java.math.BigInteger;
import java.util.Arrays;

/** One entry per vertex; priorities are distances then vertex indices. Package-only. */
final class IndexedVertexHeap {
    private final int[] heap;
    private final int[] positions;
    private final BigInteger[] distances;
    private int size;

    IndexedVertexHeap(BigInteger[] distances) {
        this.distances = distances;
        heap = new int[distances.length];
        positions = new int[distances.length];
        Arrays.fill(positions, -1);
    }

    int size() { return size; }

    /** Caller has strictly decreased an existing key, or initialized a new key. */
    void insertOrDecrease(int vertex) {
        int position = positions[vertex];
        if (position < 0) {
            position = size++;
            heap[position] = vertex;
            positions[vertex] = position;
        }
        while (position > 0) {
            int parent = (position - 1) / 2;
            if (!less(heap[position], heap[parent])) break;
            swap(position, parent);
            position = parent;
        }
    }

    int removeMin() {
        if (size == 0) throw new IllegalStateException("Empty heap.");
        int vertex = heap[0];
        swap(0, --size);
        positions[vertex] = -1;
        int position = 0;
        while (position < size / 2) {
            int child = 2 * position + 1;
            if (child + 1 < size && less(heap[child + 1], heap[child])) child++;
            if (!less(heap[child], heap[position])) break;
            swap(position, child);
            position = child;
        }
        return vertex;
    }

    private boolean less(int left, int right) {
        int comparison = distances[left].compareTo(distances[right]);
        return comparison < 0 || (comparison == 0 && left < right);
    }

    private void swap(int left, int right) {
        int vertex = heap[left];
        heap[left] = heap[right];
        heap[right] = vertex;
        positions[heap[left]] = left;
        positions[heap[right]] = right;
    }
}
