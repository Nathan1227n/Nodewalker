package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SolverExecutionOptionsTest {
    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeBudgets(long budget) {
        assertThrows(IllegalArgumentException.class, () -> SolverExecutionOptions.nodeLimit(budget));
        assertThrows(IllegalArgumentException.class, () -> SolverExecutionOptions.timeLimitMillis(budget));
    }

    @Test
    void validatesNanosecondConversionWithoutSaturation() {
        long largest = Long.MAX_VALUE / 1_000_000;
        assertEquals(largest, SolverExecutionOptions.timeLimitMillis(largest).maxElapsedMillis().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> SolverExecutionOptions.timeLimitMillis(largest + 1));
        assertThrows(IllegalArgumentException.class, () -> SolverExecutionOptions.timeLimitMillis(Long.MAX_VALUE));
    }

    @Test
    void distinguishesDisabledZeroAndMaximumNodeLimits() {
        assertEquals(0, SolverExecutionOptions.nodeLimit(0).maxVisitedNodes().orElseThrow());
        assertTrue(SolverExecutionOptions.nodeLimit(0).maxElapsedMillis().isEmpty());
        assertEquals(Long.MAX_VALUE, SolverExecutionOptions.nodeLimit(Long.MAX_VALUE).maxVisitedNodes().orElseThrow());
        assertFalse(SolverExecutionOptions.unlimited().hasLimits());
        assertTrue(SolverExecutionOptions.unlimited().allowUnlimited());
        assertTrue(SolverExecutionOptions.limits(1, 1).hasLimits());
        assertFalse(SolverExecutionOptions.timeLimitMillis(0).allowUnlimited());
    }

    @Test
    void requiresExplicitUnboundedAuthorizationAndNonNullFields() {
        var policy = SolverExecutionPolicy.demonstration();
        assertThrows(IllegalArgumentException.class, () -> new SolverExecutionOptions(
                OptionalLong.empty(), OptionalLong.empty(), false, policy));
        assertThrows(NullPointerException.class, () -> new SolverExecutionOptions(null, OptionalLong.empty(), true, policy));
        assertThrows(NullPointerException.class, () -> new SolverExecutionOptions(OptionalLong.empty(), null, true, policy));
        assertThrows(NullPointerException.class, () -> SolverExecutionOptions.unlimited().withPolicy(null));
    }

    @Test
    void validatesEvidenceAndKeepsPolicyChangesImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new SolverExecutionPolicy(2, 3, "medido"));
        assertThrows(IllegalArgumentException.class, () -> new SolverExecutionPolicy(3, 2, "medido"));
        assertThrows(IllegalArgumentException.class, () -> new SolverExecutionPolicy(3, 3, " "));
        assertThrows(NullPointerException.class, () -> new SolverExecutionPolicy(3, 3, null));
        var original = SolverExecutionOptions.nodeLimit(10);
        var policy = new SolverExecutionPolicy(4, 5, "benchmark de teste, sintético");
        var changed = original.withPolicy(policy);
        assertSame(policy, changed.policy());
        assertNotEquals(policy, original.policy());
        assertEquals(original.maxVisitedNodes(), changed.maxVisitedNodes());
    }
}
