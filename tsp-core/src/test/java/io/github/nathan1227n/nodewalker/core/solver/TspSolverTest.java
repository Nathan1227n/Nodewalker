package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.util.List;
import org.junit.jupiter.api.Test;

class TspSolverTest {
    @Test
    void supportsAJavaStrategyWithSharedInputAndRepeatableOutputWithoutSearch() {
        TspProblem problem = ContractTestProblems.triangle();
        TspRoute route = new TspRoute(List.of(0, 1, 2, 0));
        // Resultado manual conhecido para testar somente o contrato, sem implementar busca.
        TspSolver strategy = input -> {
            assertSame(problem, input);
            return TspResult.completed(input, route, 45L, SolverMetrics.empty());
        };

        TspResult first = strategy.solve(problem);
        TspResult repeated = strategy.solve(problem);

        assertEquals(SolverStatus.CONCLUIDO, first.status());
        assertEquals(route, first.route().orElseThrow());
        assertEquals(45L, first.cost().orElseThrow());
        assertEquals(first.route(), repeated.route());
        assertEquals(first.cost(), repeated.cost());
    }

    @Test
    void strategyCanDelegateNullInputValidationToResultFactory() {
        TspSolver strategy = input -> TspResult.completed(input, new TspRoute(List.of(0, 1, 2, 0)),
                45L, SolverMetrics.empty());

        assertThrows(NullPointerException.class, () -> strategy.solve(null));
    }
}
