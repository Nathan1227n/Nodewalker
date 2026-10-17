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

    public static TspProblem lateFirstOptimum() {
        return problem(new long[][] {
                {0, 9, 1, 1}, {9, 0, 1, 1}, {1, 1, 0, 9}, {1, 1, 9, 0}
        });
    }

    public static TspProblem distinctTiedCycles() {
        return problem(new long[][] {
                {0, 5, 1, 5}, {5, 0, 5, 1}, {1, 5, 0, 5}, {5, 1, 5, 0}
        });
    }

    public static TspProblem equalEdges(int n, long cost) {
        long[][] costs = new long[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                costs[from][to] = from == to ? 0 : cost;
            }
        }
        return problem(costs);
    }

    /** Um ciclo e seu inverso custam N; todo ciclo usa N arestas >= 1. */
    public static TspProblem unitRing(int n) {
        long[][] costs = new long[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                int distance = Math.abs(from - to);
                costs[from][to] = from == to ? 0 : distance == 1 || distance == n - 1 ? 1 : 1000;
            }
        }
        return problem(costs);
    }

    public static TspProblem problem(long[][] costs) {
        List<City> cities = IntStream.range(0, costs.length)
                .mapToObj(i -> new City(i, 0, 0, 0)).toList();
        return new TspProblem(cities, costs);
    }
}
