package io.github.nathan1227n.nodewalker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class SearchSpaceTest {

    @Test
    void computesFixedOriginSearchSpace() {
        assertEquals(BigInteger.valueOf(2), SearchSpace.completeRoutes(3));
        assertEquals(BigInteger.valueOf(24), SearchSpace.completeRoutes(5));
        assertEquals(BigInteger.valueOf(362_880), SearchSpace.completeRoutes(10));
    }

    @Test
    void handlesCountsBeyondLongRange() {
        assertTrue(SearchSpace.completeRoutes(22)
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test
    void rejectsInsufficientCityCount() {
        assertThrows(IllegalArgumentException.class, () -> SearchSpace.completeRoutes(2));
    }
}
