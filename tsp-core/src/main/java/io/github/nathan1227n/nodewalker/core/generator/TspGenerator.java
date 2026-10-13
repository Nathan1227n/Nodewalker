package io.github.nathan1227n.nodewalker.core.generator;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera instâncias reprodutíveis conforme DOC 03 §§3 e 18, sem dependências gráficas.
 * As coordenadas inteiras pertencem a [0, 1000] e a separação mínima 3D é 50.
 */
public final class TspGenerator {
    private static final long MINIMUM_DISTANCE_SQUARED = 2500;

    private TspGenerator() {
    }

    /**
     * Cada chamada cria seu próprio {@link Random} com a seed informada.
     * Cada tentativa consome x, y e z nessa ordem, inclusive se for rejeitada.
     * Não há limite de tentativas: valores elevados de N podem ser inviáveis
     * pela separação mínima ou pela memória da matriz N por N.
     *
     * @param n quantidade de cidades, pelo menos três
     * @param seed semente de geração, armazenada na instância retornada
     * @return problema imutável, com cidades em ordem de índice e origem fixa 0
     * @throws IllegalArgumentException se n for menor que três
     */
    public static TspProblem generate(int n, long seed) {
        if (n < 3) {
            throw new IllegalArgumentException("Uma instância TSP deve ter N >= 3: " + n);
        }
        Random random = new Random(seed);
        List<City> cities = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            City candidate;
            do {
                int x = random.nextInt(0, 1001);
                int y = random.nextInt(0, 1001);
                int z = random.nextInt(0, 1001);
                candidate = new City(i, x, y, z);
            } while (!isFarEnough(candidate, cities));
            cities.add(candidate);
        }
        long[][] costs = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                long cost = roundedDistance(cities.get(i), cities.get(j));
                costs[i][j] = cost;
                costs[j][i] = cost;
            }
        }
        return new TspProblem(cities, costs, seed);
    }

    // Auxiliares internos para coordenadas em [0, 1000], também usados nos testes geométricos.
    static boolean isFarEnough(City candidate, List<City> cities) {
        for (City city : cities) {
            if (squaredDistance(candidate, city) < MINIMUM_DISTANCE_SQUARED) {
                return false;
            }
        }
        return true;
    }

    static long roundedDistance(City from, City to) {
        return Math.round(Math.sqrt(squaredDistance(from, to)));
    }

    private static long squaredDistance(City from, City to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        // |delta| <= 1000; a soma é no máximo 3_000_000, exata em long e double.
        return dx * dx + dy * dy + dz * dz;
    }
}
