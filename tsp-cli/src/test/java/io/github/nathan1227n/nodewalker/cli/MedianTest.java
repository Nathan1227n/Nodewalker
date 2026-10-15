package io.github.nathan1227n.nodewalker.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MedianTest {
    @ParameterizedTest
    @MethodSource("samples")
    void computesTheExactMedianWithoutChangingInput(long[] samples, String expected) {
        long[] original = samples.clone();

        assertEquals(0, new BigDecimal(expected).compareTo(Median.of(samples)));
        assertArrayEquals(original, samples);
    }

    @Test
    void rejectsEmptyOrNegativeMeasurements() {
        assertThrows(IllegalArgumentException.class, () -> Median.of(new long[0]));
        assertThrows(IllegalArgumentException.class, () -> Median.of(new long[] {0, -1, 2}));
    }

    private static Stream<Arguments> samples() {
        return Stream.of(
                Arguments.of(new long[] {0}, "0"),
                Arguments.of(new long[] {8}, "8"),
                Arguments.of(new long[] {9, 1, 5}, "5"),
                Arguments.of(new long[] {8, 1, 2, 9}, "5"),
                Arguments.of(new long[] {2, 1}, "1.5"),
                Arguments.of(new long[] {7, 7, 7, 7}, "7"),
                Arguments.of(new long[] {0, 1}, "0.5"),
                Arguments.of(new long[] {Long.MAX_VALUE}, "9223372036854775807"),
                Arguments.of(new long[] {Long.MAX_VALUE - 1, Long.MAX_VALUE}, "9223372036854775806.5"),
                Arguments.of(new long[] {Long.MAX_VALUE, Long.MAX_VALUE}, "9223372036854775807"));
    }
}
