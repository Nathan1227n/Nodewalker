package io.github.nathan1227n.nodewalker.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TspRouteTest {
    @ParameterizedTest
    @MethodSource("validCycles")
    void preservesCompleteCycleIncludingFinalZero(List<Integer> indices) {
        TspRoute route = new TspRoute(indices);

        assertEquals(indices, route.indices());
        assertEquals(indices.size() - 1, route.cityCount());
        assertEquals(0, route.indices().getFirst());
        assertEquals(0, route.indices().getLast());
    }

    private static Stream<List<Integer>> validCycles() {
        return Stream.of(List.of(0, 1, 2, 0), List.of(0, 2, 1, 0),
                List.of(0, 1, 3, 2, 0), List.of(0, 2, 4, 1, 3, 0));
    }

    @ParameterizedTest
    @MethodSource("tooShortCycles")
    void rejectsCyclesWithFewerThanThreeCities(List<Integer> indices) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new TspRoute(indices));

        assertTrue(error.getMessage().contains("N >= 3"));
    }

    private static Stream<List<Integer>> tooShortCycles() {
        return Stream.of(List.of(), List.of(0), List.of(0, 0), List.of(0, 1, 0));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "3, 2", "0, -1", "3, 3"})
    void rejectsIncorrectInitialOrFinalOrigin(int position, int index) {
        List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 0));
        indices.set(position, index);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new TspRoute(indices));

        assertTrue(error.getMessage().contains("iniciar e terminar"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE, 3, Integer.MAX_VALUE})
    void rejectsIndicesOutsideTheInferredCityRange(int index) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TspRoute(List.of(0, index, 2, 0)));

        assertTrue(error.getMessage().contains("posição 1"));
        assertTrue(error.getMessage().contains("[0, 3)"));
    }

    @ParameterizedTest
    @MethodSource("repeatedCities")
    void rejectsRepeatedCitiesAndTheConsequentMissingCity(List<Integer> indices) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new TspRoute(indices));

        assertTrue(error.getMessage().contains("repetida"));
    }

    private static Stream<List<Integer>> repeatedCities() {
        return Stream.of(List.of(0, 1, 1, 3, 0), List.of(0, 1, 2, 2, 0), List.of(0, 1, 2, 3, 1, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void rejectsOriginAtAnyInteriorPosition(int position) {
        List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 3, 0));
        indices.set(position, 0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new TspRoute(indices));

        assertTrue(error.getMessage().contains("posição interna " + position));
    }

    @Test
    void rejectsNullList() {
        assertThrows(NullPointerException.class, () -> new TspRoute(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void rejectsNullIndexAtAnyPosition(int position) {
        List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 0));
        indices.set(position, null);

        NullPointerException error = assertThrows(NullPointerException.class, () -> new TspRoute(indices));

        assertTrue(error.getMessage().contains("posição " + position));
    }

    @Test
    void snapshotsMutableInputAndNeverExposesAMutableList() {
        List<Integer> input = new ArrayList<>(List.of(0, 2, 1, 0));
        TspRoute route = new TspRoute(input);
        input.set(1, 0);
        input.add(3);

        assertEquals(List.of(0, 2, 1, 0), route.indices());
        assertThrows(UnsupportedOperationException.class, () -> route.indices().set(1, 1));
        assertThrows(UnsupportedOperationException.class, () -> route.indices().add(0));
        assertThrows(UnsupportedOperationException.class, () -> route.indices().clear());
    }

    @Test
    void comparesRoutesByTheirStoredSequence() {
        TspRoute route = new TspRoute(List.of(0, 1, 2, 0));
        TspRoute repeated = new TspRoute(new ArrayList<>(List.of(0, 1, 2, 0)));

        assertEquals(route, repeated);
        assertEquals(route.hashCode(), repeated.hashCode());
        assertNotEquals(route, new TspRoute(List.of(0, 2, 1, 0)));
    }

    @Test
    void canonicalizesOnlyOrientationAndPreservesOriginalRoute() {
        TspRoute reverse = new TspRoute(List.of(0, 2, 3, 1, 0));
        TspRoute canonical = reverse.canonical();

        assertFalse(reverse.isCanonical());
        assertTrue(canonical.isCanonical());
        assertEquals(List.of(0, 1, 3, 2, 0), canonical.indices());
        assertEquals(List.of(0, 2, 3, 1, 0), reverse.indices());
        assertNotEquals(List.of(0, 1, 2, 3, 0), canonical.indices());
        assertEquals(canonical, canonical.canonical());
    }

    @Test
    void canonicalOrientationOfThreeCitiesUsesTheTwoInteriorIndices() {
        assertEquals(new TspRoute(List.of(0, 1, 2, 0)), new TspRoute(List.of(0, 2, 1, 0)).canonical());
    }
}
