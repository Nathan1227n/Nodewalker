package io.github.nathan1227n.nodewalker.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TspProblemCheckpointTest {
    private static final List<City> CITIES = List.of(new City(0, 0, 0, 0), new City(1, 0, 0, 0), new City(2, 0, 0, 0));

    @Test
    void copyFactoryPreservesSeedDefensiveCopiesAndImmutableCities() {
        List<City> cities = new ArrayList<>(CITIES);
        long[][] costs = triangle();
        AtomicInteger checkpoints = new AtomicInteger();
        TspProblem problem = TspProblem.copyOf(cities, costs, 657L, checkpoints::incrementAndGet);
        cities.clear();
        costs[0][1] = -1;
        costs[1] = new long[] {99, 99, 99};

        assertEquals(657L, problem.seed().orElseThrow());
        assertEquals(CITIES, problem.cities());
        assertEquals(10L, problem.cost(0, 1));
        assertEquals(15L, problem.cost(1, 2));
        assertTrue(checkpoints.get() > 1);
        assertThrows(UnsupportedOperationException.class, () -> problem.cities().clear());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 16, 25})
    void propagatesCheckpointCancellationWithoutReturningAnInstance(int stopAt) {
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<TspProblem> returned = new AtomicReference<>();
        CancellationException cancellation = new CancellationException("Cancelamento de teste");

        CancellationException failure = assertThrows(CancellationException.class,
                () -> returned.set(TspProblem.copyOf(CITIES, triangle(), 0L, () -> {
                    if (checks.incrementAndGet() == stopAt) {
                        throw cancellation;
                    }
                })));

        assertSame(cancellation, failure);
        assertEquals(stopAt, checks.get());
        assertEquals(null, returned.get());
    }

    @Test
    void preservesDomainAndNullValidation() {
        Runnable checkpoint = () -> { };
        long[][] asymmetric = triangle();
        asymmetric[0][1]++;

        assertThrows(IllegalArgumentException.class, () -> TspProblem.copyOf(CITIES, asymmetric, 0L, checkpoint));
        assertThrows(IllegalArgumentException.class, () -> TspProblem.copyOf(List.of(), new long[0][0], 0L, checkpoint));
        assertThrows(IllegalArgumentException.class,
                () -> TspProblem.copyOf(List.of(CITIES.get(1), CITIES.get(0), CITIES.get(2)), triangle(), 0L, checkpoint));
        assertThrows(NullPointerException.class, () -> TspProblem.copyOf(null, triangle(), 0L, checkpoint));
        assertThrows(NullPointerException.class, () -> TspProblem.copyOf(CITIES, null, 0L, checkpoint));
        assertThrows(NullPointerException.class, () -> TspProblem.copyOf(CITIES, triangle(), 0L, null));
    }

    @Test
    void propagatesRealCheckpointFailuresUnchanged() {
        IllegalStateException error = new IllegalStateException("Falha real de teste");

        assertSame(error, assertThrows(IllegalStateException.class,
                () -> TspProblem.copyOf(CITIES, triangle(), 0L, () -> { throw error; })));
    }

    private static long[][] triangle() {
        return new long[][] {{0, 10, 20}, {10, 0, 15}, {20, 15, 0}};
    }
}
