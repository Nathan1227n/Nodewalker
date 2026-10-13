package io.github.nathan1227n.nodewalker.core;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.List;
import java.util.stream.IntStream;

/** Matrizes manuais compartilhadas pelos testes de contratos; sem busca ou geometria inferida. */
public final class ContractTestProblems {
    private ContractTestProblems() {
    }

    public static TspProblem triangle() {
        return triangle(10, 15, 20);
    }

    public static TspProblem triangle(long edge01, long edge12, long edge20) {
        return problem(new long[][] {
                {0, edge01, edge20},
                {edge01, 0, edge12},
                {edge20, edge12, 0}
        });
    }

    public static TspProblem fourCityReference() {
        return problem(new long[][] {
                {0, 80, 100, 90},
                {80, 0, 70, 110},
                {100, 70, 0, 60},
                {90, 110, 60, 0}
        });
    }

    public static TspProblem equalEdges() {
        return problem(new long[][] {
                {0, 7, 7, 7},
                {7, 0, 7, 7},
                {7, 7, 0, 7},
                {7, 7, 7, 0}
        });
    }

    private static TspProblem problem(long[][] costs) {
        List<City> cities = IntStream.range(0, costs.length)
                .mapToObj(i -> new City(i, 0, 0, 0)).toList();
        return new TspProblem(cities, costs);
    }
}
