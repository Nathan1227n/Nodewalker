package io.github.nathan1227n.nodewalker.core.generator;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        validateCityCount(n);
        return generateInstance(n, seed, new GenerationControl(n, null, null));
    }

    /**
     * Gera a mesma instância da API original quando termina dentro dos orçamentos.
     * Verifica limites antes de grandes alocações, conta candidatos rejeitados e
     * observa token e interrupção da thread sem limpar o sinal de interrupção.
     * Nenhuma instância parcial é devolvida; cancelamento de geração não é resultado de solver.
     *
     * @param n quantidade de cidades, pelo menos três
     * @param seed semente determinística
     * @param options orçamentos explícitos e token de cancelamento
     * @return problema completo, válido e imutável
     * @throws NullPointerException se options for nulo
     * @throws IllegalArgumentException se n for menor que três
     * @throws GenerationLimitExceededException se o orçamento de candidatos for insuficiente
     * @throws GenerationMemoryLimitExceededException se a estimativa exceder o orçamento de bytes
     * @throws GenerationCancelledException se token ou interrupção forem observados
     */
    public static TspProblem generate(int n, long seed, GenerationOptions options) {
        return generate(n, seed, options, null);
    }

    /**
     * Modelo portátil de pico: 16*N² + 256*N + 1024 bytes, em aritmética exata.
     * Inclui duas matrizes long e reservas estimadas para cidades, referências e
     * estruturas auxiliares. Não mede o layout da JVM, reserva heap ou impede todo OOME.
     *
     * @param n quantidade de cidades, pelo menos três
     * @return estimativa, inclusive quando maior que Long.MAX_VALUE
     * @throws IllegalArgumentException se n for menor que três
     */
    public static BigInteger estimatePeakMemoryBytes(int n) {
        validateCityCount(n);
        BigInteger count = BigInteger.valueOf(n);
        return count.multiply(count).multiply(BigInteger.valueOf(16))
                .add(count.multiply(BigInteger.valueOf(256))).add(BigInteger.valueOf(1024));
    }

    // Gancho interno para testes determinísticos; não é um listener público de produto.
    enum Phase { PREFLIGHT, CITIES, MATRIX_ALLOCATION, MATRIX_COSTS, FINALIZATION, READY }

    @FunctionalInterface
    interface CheckpointObserver {
        void onCheckpoint(Phase phase, long attemptedCandidates, int acceptedCities);
    }

    static TspProblem generate(int n, long seed, GenerationOptions options, CheckpointObserver observer) {
        validateCityCount(n);
        Objects.requireNonNull(options, "As opções de geração não podem ser nulas.");
        GenerationControl control = new GenerationControl(n, options, observer);
        control.checkpoint(Phase.PREFLIGHT);
        if (options.maxCandidates() < n) {
            throw control.limitExceeded();
        }
        BigInteger estimate = estimatePeakMemoryBytes(n);
        if (estimate.compareTo(BigInteger.valueOf(options.maxEstimatedBytes())) > 0) {
            throw new GenerationMemoryLimitExceededException(estimate, options.maxEstimatedBytes());
        }
        return generateInstance(n, seed, control);
    }

    private static void validateCityCount(int n) {
        if (n < 3) {
            throw new IllegalArgumentException("Uma instância TSP deve ter N >= 3: " + n);
        }
    }

    private static TspProblem generateInstance(int n, long seed, GenerationControl control) {
        control.checkpoint(Phase.CITIES);
        Random random = new Random(seed);
        List<City> cities = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            City candidate;
            do {
                control.beforeCandidate();
                // Um candidato sempre consome o trio inteiro, sem checkpoint entre coordenadas.
                int x = random.nextInt(0, 1001);
                int y = random.nextInt(0, 1001);
                int z = random.nextInt(0, 1001);
                control.candidateDrawn();
                control.checkpoint(Phase.CITIES);
                candidate = new City(i, x, y, z);
            } while (!isFarEnough(candidate, cities, control));
            cities.add(candidate);
            control.cityAccepted();
        }
        control.checkpoint(Phase.MATRIX_ALLOCATION);
        long[][] costs = new long[n][];
        for (int i = 0; i < n; i++) {
            control.checkpoint(Phase.MATRIX_ALLOCATION);
            costs[i] = new long[n];
            control.checkpoint(Phase.MATRIX_ALLOCATION);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                control.checkpoint(Phase.MATRIX_COSTS);
                long cost = roundedDistance(cities.get(i), cities.get(j));
                costs[i][j] = cost;
                costs[j][i] = cost;
            }
        }
        control.checkpoint(Phase.FINALIZATION);
        TspProblem problem = TspProblem.copyOf(cities, costs, seed,
                () -> control.checkpoint(Phase.FINALIZATION));
        control.checkpoint(Phase.READY);
        return problem;
    }

    // Auxiliares internos para coordenadas em [0, 1000], também usados nos testes geométricos.
    static boolean isFarEnough(City candidate, List<City> cities) {
        return isFarEnough(candidate, cities, new GenerationControl(cities.size(), null, null));
    }

    private static boolean isFarEnough(City candidate, List<City> cities, GenerationControl control) {
        for (City city : cities) {
            control.checkpoint(Phase.CITIES);
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

    private static final class GenerationControl {
        private final int requestedCities;
        private final GenerationOptions options;
        private final CheckpointObserver observer;
        private long attemptedCandidates;
        private int acceptedCities;

        private GenerationControl(int requestedCities, GenerationOptions options, CheckpointObserver observer) {
            this.requestedCities = requestedCities;
            this.options = options;
            this.observer = observer;
        }

        private void checkpoint(Phase phase) {
            if (options == null) {
                return; // A API original continua sem limites e sem observar cancelamento/interrupção.
            }
            if (observer != null) {
                observer.onCheckpoint(phase, attemptedCandidates, acceptedCities);
            }
            if (options.cancellationToken().isCancellationRequested() || Thread.currentThread().isInterrupted()) {
                throw new GenerationCancelledException(attemptedCandidates, acceptedCities);
            }
        }

        private void beforeCandidate() {
            checkpoint(Phase.CITIES);
            if (options != null && attemptedCandidates >= options.maxCandidates()) {
                throw limitExceeded();
            }
        }

        private void candidateDrawn() {
            if (options != null) {
                // beforeCandidate garante attemptedCandidates < maxCandidates <= Long.MAX_VALUE.
                attemptedCandidates++;
            }
        }

        private void cityAccepted() {
            if (options != null) {
                acceptedCities++;
            }
        }

        private GenerationLimitExceededException limitExceeded() {
            return new GenerationLimitExceededException(requestedCities, options.maxCandidates(),
                    attemptedCandidates, acceptedCities);
        }
    }
}
