package io.github.nathan1227n.nodewalker.core.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationOptionsTest {
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveCandidateBudgets(long budget) {
        assertThrows(IllegalArgumentException.class, () -> new GenerationOptions(budget, 4096));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveMemoryBudgets(long budget) {
        assertThrows(IllegalArgumentException.class, () -> new GenerationOptions(5, budget));
    }

    @Test
    void rejectsNullCancellationToken() {
        assertThrows(NullPointerException.class, () -> new GenerationOptions(5, 4096, null));
    }

    @Test
    void preservesExplicitBudgetsAndTokenIncludingMaximumLongBudgets() {
        GenerationCancellationToken token = new GenerationCancellationToken();
        GenerationOptions options = new GenerationOptions(Long.MAX_VALUE, Long.MAX_VALUE, token);

        assertEquals(Long.MAX_VALUE, options.maxCandidates());
        assertEquals(Long.MAX_VALUE, options.maxEstimatedBytes());
        assertSame(token, options.cancellationToken());
    }

    @Test
    void convenienceConstructorCreatesIndependentTokens() {
        GenerationOptions first = new GenerationOptions(5, 4096);
        GenerationOptions second = new GenerationOptions(5, 4096);

        assertNotSame(first.cancellationToken(), second.cancellationToken());
        first.cancellationToken().cancel();
        assertTrue(first.cancellationToken().isCancellationRequested());
        assertFalse(second.cancellationToken().isCancellationRequested());
    }

    @Test
    void cancellationIsPersistentAndIdempotent() {
        GenerationCancellationToken token = new GenerationCancellationToken();
        assertFalse(token.isCancellationRequested());

        token.cancel();
        token.cancel();

        assertTrue(token.isCancellationRequested());
    }
}
