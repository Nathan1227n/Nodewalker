package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Oráculo matemático somente de testes: DP de sufixos, sem enumeração ou poda.
 * D(v,S) é o menor custo de sair de v, visitar S e voltar a 0.
 * Tempo O(N² * 2^(N-1)), espaço O(N * 2^(N-1)), com custos BigInteger.
 */
final class HeldKarpOracle {
    static final int MAX_CITIES = 12;

    private HeldKarpOracle() {
    }

    record Optimum(BigInteger cost, List<Integer> route) {
        Optimum {
            Objects.requireNonNull(cost);
            route = List.copyOf(route);
        }

        /** Representabilidade matemática não garante conclusão de uma execução concreta. */
        OptionalLong longCost() {
            return cost.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                    ? OptionalLong.of(cost.longValueExact()) : OptionalLong.empty();
        }
    }

    static Optimum solve(TspProblem problem) {
        Objects.requireNonNull(problem);
        int n = problem.size();
        // Verificar antes do shift e da alocação exponencial. Este teto não pertence ao produto.
        if (n > MAX_CITIES) {
            throw new IllegalArgumentException("Held–Karp de testes exige N <= " + MAX_CITIES + ": N=" + n);
        }
        BigInteger[][] costs = new BigInteger[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                costs[from][to] = BigInteger.valueOf(problem.cost(from, to));
            }
        }
        int full = (1 << (n - 1)) - 1;
        BigInteger[][] remainingCost = new BigInteger[full + 1][n];
        for (int v = 0; v < n; v++) {
            remainingCost[0][v] = costs[v][0];
        }
        // Remover um bit sempre reduz a máscara: dependências já foram calculadas.
        for (int mask = 1; mask <= full; mask++) {
            for (int v = 0; v < n; v++) {
                if (v > 0 && (mask & (1 << (v - 1))) != 0) {
                    continue; // A cidade atual não pode estar entre as ainda não visitadas.
                }
                BigInteger best = null;
                // Ordem deliberadamente decrescente; nenhum predecessor é guardado.
                for (int u = n - 1; u >= 1; u--) {
                    int bit = 1 << (u - 1);
                    if ((mask & bit) != 0) {
                        BigInteger candidate = costs[v][u].add(remainingCost[mask ^ bit][u]);
                        if (best == null || candidate.compareTo(best) < 0) {
                            best = candidate;
                        }
                    }
                }
                remainingCost[mask][v] = best;
            }
        }

        List<Integer> route = new ArrayList<>(n + 1);
        route.add(0);
        int current = 0;
        int mask = full;
        while (mask != 0) {
            boolean found = false;
            // Menor índice que ainda permite o ótimo: primeira rota lexicográfica, por indução.
            for (int next = 1; next < n; next++) {
                int bit = 1 << (next - 1);
                if ((mask & bit) != 0 && costs[current][next].add(remainingCost[mask ^ bit][next])
                        .equals(remainingCost[mask][current])) {
                    route.add(next);
                    current = next;
                    mask ^= bit;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("A tabela ótima não permite reconstruir o ciclo.");
            }
        }
        route.add(0);
        // O inverso também é ótimo na matriz simétrica. O primeiro lexicográfico já é canônico.
        return new Optimum(remainingCost[full][0], route);
    }
}
