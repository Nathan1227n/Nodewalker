package io.github.nathan1227n.nodewalker.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CityTest {

    @Test
    void storesIndexAndIntegerCoordinates() {
        City city = new City(0, 120, 300, 50);

        assertEquals(0, city.index());
        assertEquals(120, city.x());
        assertEquals(300, city.y());
        assertEquals(50, city.z());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void rejectsNegativeIndex(int index) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new City(index, 0, 0, 0));

        assertTrue(error.getMessage().contains("índice"));
    }

    @Test
    void leavesCoordinateRangeRestrictionsToTheGenerator() {
        City city = new City(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, -1);

        assertEquals(Integer.MAX_VALUE, city.index());
        assertEquals(Integer.MIN_VALUE, city.x());
        assertEquals(Integer.MAX_VALUE, city.y());
        assertEquals(-1, city.z());
    }
}
