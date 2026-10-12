package io.github.nathan1227n.nodewalker.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CostMathTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "10, 15, 25",
            "9223372036854775807, 0, 9223372036854775807",
            "0, 9223372036854775807, 9223372036854775807",
            "9223372036854775797, 10, 9223372036854775807"
    })
    void addsCostsExactlyUpToLongMaximum(long left, long right, long expected) {
        assertEquals(expected, CostMath.addExact(left, right));
    }

    @ParameterizedTest
    @CsvSource({
            "9223372036854775807, 1",
            "9223372036854775807, 9223372036854775807",
            "9223372036854775802, 6"
    })
    void rejectsOverflowWithoutReturningAWrappedOrSaturatedCost(long left, long right) {
        ArithmeticException error = assertThrows(ArithmeticException.class,
                () -> CostMath.addExact(left, right));

        assertTrue(error.getMessage().contains("Long.MAX_VALUE"));
        assertInstanceOf(ArithmeticException.class, error.getCause());
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "0, -1", "-1, -1", "-9223372036854775808, 1"})
    void rejectsNegativeOperands(long left, long right) {
        assertThrows(IllegalArgumentException.class, () -> CostMath.addExact(left, right));
    }
}
