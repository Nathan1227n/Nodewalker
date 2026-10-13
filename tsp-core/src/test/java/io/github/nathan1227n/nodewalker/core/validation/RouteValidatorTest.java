package io.github.nathan1227n.nodewalker.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RouteValidatorTest {
    private static final TspRoute TRIANGLE = new TspRoute(List.of(0, 1, 2, 0));

    @Test
    void sumsAllThreeMatrixEdgesIncludingReturnEvenWithCoincidentCoordinates() {
        TspProblem problem = ContractTestProblems.triangle();

        assertEquals(45L, RouteValidator.validateAndCost(problem, TRIANGLE));
        assertEquals(0, problem.cities().get(0).x());
        assertEquals(problem.cities().get(0).x(), problem.cities().get(1).x());
        assertEquals(0L, RouteValidator.validateAndCost(ContractTestProblems.triangle(0, 0, 0), TRIANGLE));
    }

    @Test
    void acceptsBothOrientationsWithoutNormalizingTheInput() {
        TspProblem problem = ContractTestProblems.fourCityReference();
        TspRoute forward = new TspRoute(List.of(0, 1, 2, 3, 0));
        TspRoute reverse = new TspRoute(List.of(0, 3, 2, 1, 0));

        assertEquals(300L, RouteValidator.validateAndCost(problem, forward));
        assertEquals(300L, RouteValidator.validateAndCost(problem, reverse));
        assertEquals(List.of(0, 3, 2, 1, 0), reverse.indices());
    }

    @Test
    void reportsCostWithoutSelectingBetweenDistinctEqualCostCycles() {
        TspProblem problem = ContractTestProblems.equalEdges();
        TspRoute first = new TspRoute(List.of(0, 1, 2, 3, 0));
        TspRoute other = new TspRoute(List.of(0, 1, 3, 2, 0));

        assertEquals(28L, RouteValidator.validateAndCost(problem, first));
        assertEquals(28L, RouteValidator.validateAndCost(problem, other));
        assertEquals(List.of(0, 1, 3, 2, 0), other.indices());
    }

    @Test
    void acceptsAValidCycleWhoseCostIsNotTheKnownGlobalOptimum() {
        TspRoute nonOptimal = new TspRoute(List.of(0, 1, 3, 2, 0));

        assertEquals(350L, RouteValidator.validateAndCost(ContractTestProblems.fourCityReference(), nonOptimal));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsCycleLengthIncompatibleWithProblem(boolean largerRoute) {
        TspProblem problem = largerRoute ? ContractTestProblems.triangle() : ContractTestProblems.fourCityReference();
        TspRoute route = largerRoute ? new TspRoute(List.of(0, 1, 2, 3, 0)) : TRIANGLE;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RouteValidator.validateAndCost(problem, route));

        assertTrue(error.getMessage().contains("posições"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsNullProblemOrRoute(boolean nullProblem) {
        assertThrows(NullPointerException.class,
                () -> RouteValidator.validateAndCost(nullProblem ? null : ContractTestProblems.triangle(),
                        nullProblem ? TRIANGLE : null));
    }

    @Test
    void acceptsLongMaximumCostWhenTheWholeCycleIsRepresentable() {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, 0, 0);

        assertEquals(Long.MAX_VALUE, RouteValidator.validateAndCost(problem, TRIANGLE));
        assertEquals(Long.MAX_VALUE, RouteValidator.validateAndCost(problem, new TspRoute(List.of(0, 2, 1, 0))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagatesOverflowOnAnInteriorEdgeOrOnTheReturnEdge(boolean onReturn) {
        TspProblem problem = ContractTestProblems.triangle(Long.MAX_VALUE, onReturn ? 0 : 1, onReturn ? 1 : 0);

        ArithmeticException error = assertThrows(ArithmeticException.class,
                () -> RouteValidator.validateAndCost(problem, TRIANGLE));

        assertTrue(error.getMessage().contains("Long.MAX_VALUE"));
        assertNotNull(error.getCause());
    }
}
