package io.github.nathan1227n.nodewalker.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Instância imutável de um TSP completo e simétrico, com origem fixa no índice 0.
 * Os custos manuais são independentes das coordenadas das cidades.
 * Todas as arestas não negativas representáveis em {@code long} são aceitas;
 * somas devem usar {@link CostMath#addExact(long, long)} para detectar overflow.
 */
public final class TspProblem {
    private static final Runnable NO_CHECKPOINT = () -> { };
    private final List<City> cities;
    private final long[][] costs;
    private final OptionalLong seed;

    /**
     * Valida e copia cidades e custos, sem reter referências mutáveis do chamador.
     * As posições na lista de cidades devem corresponder aos índices 0 até N-1.
     *
     * @param cities lista ordenada por índice com pelo menos três cidades
     * @param costs matriz N por N, simétrica, não negativa e com diagonal zero
     * @throws NullPointerException se a lista, uma cidade, a matriz ou uma linha for nula
     * @throws IllegalArgumentException se quantidade, índices ou custos violarem o contrato
     */
    public TspProblem(List<City> cities, long[][] costs) {
        this(cities, costs, OptionalLong.empty());
    }

    /**
     * Valida e copia cidades e custos com as mesmas regras do construtor manual,
     * preservando também a seed de geração como metadado imutável (DOC 02 §12).
     * A seed não altera nem substitui as validações das cidades e da matriz.
     *
     * @param cities lista ordenada por índice com pelo menos três cidades
     * @param costs matriz N por N, simétrica, não negativa e com diagonal zero
     * @param seed semente associada à geração da instância
     * @throws NullPointerException se a lista, uma cidade, a matriz ou uma linha for nula
     * @throws IllegalArgumentException se quantidade, índices ou custos violarem o contrato
     */
    public TspProblem(List<City> cities, long[][] costs, long seed) {
        this(cities, costs, OptionalLong.of(seed));
    }

    private TspProblem(List<City> cities, long[][] costs, OptionalLong seed) {
        this(cities, costs, seed, NO_CHECKPOINT);
    }

    /**
     * Copia e valida uma instância com checkpoints cooperativos, sem reter o callback.
     * Mantém as mesmas validações e cópias defensivas dos construtores existentes.
     * O callback deve somente verificar controle de execução, sem modificar as entradas;
     * qualquer falha é propagada e impede a devolução da instância.
     * Alocações, clones de linhas e cópias de listas são operações indivisíveis;
     * o callback é executado antes/depois delas e nos laços de validação.
     *
     * @param cities lista ordenada por índice com pelo menos três cidades
     * @param costs matriz N por N, simétrica, não negativa e com diagonal zero
     * @param seed metadado de geração
     * @param checkpoint verificação cooperativa, sem dependência de gerador ou UI
     * @return instância completa, válida e imutável
     * @throws NullPointerException se uma entrada obrigatória ou checkpoint for nulo
     * @throws IllegalArgumentException se cidades ou matriz violarem o contrato
     */
    public static TspProblem copyOf(List<City> cities, long[][] costs, long seed, Runnable checkpoint) {
        return new TspProblem(cities, costs, OptionalLong.of(seed), checkpoint);
    }

    private TspProblem(List<City> cities, long[][] costs, OptionalLong seed, Runnable checkpoint) {
        Objects.requireNonNull(checkpoint, "O checkpoint não pode ser nulo.");
        this.cities = copyCities(cities, checkpoint);
        this.costs = copyCosts(costs, this.cities.size(), checkpoint);
        this.seed = seed;
        checkpoint.run();
    }

    private static List<City> copyCities(List<City> source, Runnable checkpoint) {
        Objects.requireNonNull(source, "A lista de cidades não pode ser nula.");
        checkpoint.run();
        List<City> snapshot = new ArrayList<>(source);
        checkpoint.run();
        if (snapshot.size() < 3) {
            throw new IllegalArgumentException("Uma instância TSP deve ter N >= 3: " + snapshot.size());
        }
        for (int i = 0; i < snapshot.size(); i++) {
            checkpoint.run();
            City city = Objects.requireNonNull(snapshot.get(i), "A cidade na posição " + i + " não pode ser nula.");
            if (city.index() != i) {
                throw new IllegalArgumentException("O índice da cidade na posição " + i
                        + " deve ser " + i + ", mas é " + city.index() + ".");
            }
        }
        checkpoint.run();
        List<City> immutable = List.copyOf(snapshot);
        checkpoint.run();
        return immutable;
    }

    private static long[][] copyCosts(long[][] source, int size, Runnable checkpoint) {
        Objects.requireNonNull(source, "A matriz de custos não pode ser nula.");
        if (source.length != size) {
            throw new IllegalArgumentException("A matriz deve ter " + size + " linhas, mas tem " + source.length + ".");
        }
        checkpoint.run();
        long[][] snapshot = new long[size][];
        checkpoint.run();
        for (int i = 0; i < size; i++) {
            checkpoint.run();
            long[] row = Objects.requireNonNull(source[i], "A linha " + i + " da matriz não pode ser nula.");
            if (row.length != size) {
                throw new IllegalArgumentException("A linha " + i + " deve ter " + size
                        + " custos, mas tem " + row.length + ".");
            }
            snapshot[i] = row.clone();
            checkpoint.run();
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                checkpoint.run();
                if (snapshot[i][j] < 0) {
                    throw new IllegalArgumentException("O custo (" + i + ", " + j + ") deve ser não negativo.");
                }
                if (i == j && snapshot[i][j] != 0) {
                    throw new IllegalArgumentException("A diagonal (" + i + ", " + i + ") deve ser zero.");
                }
                if (snapshot[i][j] != snapshot[j][i]) {
                    throw new IllegalArgumentException("A matriz deve ser simétrica: custo (" + i + ", " + j
                            + ") difere de (" + j + ", " + i + ").");
                }
            }
        }
        return snapshot;
    }

    /** @return número de cidades, sempre maior ou igual a três */
    public int size() {
        return cities.size();
    }

    /**
     * @param from índice da cidade de partida
     * @param to índice da cidade de chegada
     * @return custo não negativo da aresta; zero quando os índices são iguais
     * @throws IndexOutOfBoundsException se algum índice estiver fora de [0, size())
     */
    public long cost(int from, int to) {
        Objects.checkIndex(from, size());
        Objects.checkIndex(to, size());
        return costs[from][to];
    }

    /** @return lista imutável de cidades, ordenada pelo índice; tentativas de alteração falham */
    public List<City> cities() {
        return cities;
    }

    /** @return índice fixo da origem, sempre 0 */
    public int originIndex() {
        return 0;
    }

    /** @return seed de geração, ou vazio para instâncias construídas sem esse metadado */
    public OptionalLong seed() {
        return seed;
    }
}
