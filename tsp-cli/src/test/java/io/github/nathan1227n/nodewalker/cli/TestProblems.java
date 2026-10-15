package io.github.nathan1227n.nodewalker.cli;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.solver.SolverMetrics;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.util.List;
import java.util.stream.IntStream;

final class TestProblems {
    private TestProblems() {
    }

    static TspProblem triangle() {
        return problem(new long[][] {{0, 10, 20}, {10, 0, 15}, {20, 15, 0}});
    }

    static TspProblem fourCities(boolean tied) {
        return problem(tied ? new long[][] {
                {0, 7, 7, 7}, {7, 0, 7, 7}, {7, 7, 0, 7}, {7, 7, 7, 0}
        } : new long[][] {
                {0, 80, 100, 90}, {80, 0, 70, 110}, {100, 70, 0, 60}, {90, 110, 60, 0}
        });
    }

    static TspProblem problem(long[][] costs) {
        List<City> cities = IntStream.range(0, costs.length).mapToObj(i -> new City(i, 0, 0, 0)).toList();
        return new TspProblem(cities, costs, 0);
    }

    static TspResult completed(TspProblem problem, List<Integer> indices, SolverMetrics metrics) {
        TspRoute route = new TspRoute(indices);
        return TspResult.completed(problem, route, RouteValidator.validateAndCost(problem, route), metrics);
    }

    static TspResult completed(TspProblem problem) {
        return completed(problem, List.of(0, 1, 2, 0), SolverMetrics.empty());
    }
}
