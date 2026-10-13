package io.github.nathan1227n.nodewalker.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ciclo hamiltoniano imutável com origem 0 nas duas extremidades.
 * A representação inclui o zero final: N cidades ocupam N+1 posições.
 * N é inferido do comprimento; a compatibilidade com um problema específico
 * é verificada por {@code RouteValidator.validateAndCost}.
 * Ambas as orientações são válidas; a construção preserva a sequência recebida.
 *
 * @param indices sequência completa de índices, incluindo o retorno à origem
 */
public record TspRoute(List<Integer> indices) {
    /**
     * Copia e valida a sequência, sem reter referências mutáveis do chamador.
     *
     * @throws NullPointerException se a lista ou algum índice for nulo
     * @throws IllegalArgumentException se a sequência não formar um ciclo com N >= 3
     */
    public TspRoute {
        Objects.requireNonNull(indices, "A lista de índices da rota não pode ser nula.");
        List<Integer> snapshot = new ArrayList<>(indices);
        if (snapshot.size() < 4) {
            throw new IllegalArgumentException("Um ciclo TSP com N >= 3 deve ter pelo menos 4 posições: "
                    + snapshot.size() + ".");
        }
        for (int i = 0; i < snapshot.size(); i++) {
            Objects.requireNonNull(snapshot.get(i), "O índice na posição " + i + " não pode ser nulo.");
        }
        int n = snapshot.size() - 1;
        if (snapshot.getFirst() != 0 || snapshot.getLast() != 0) {
            throw new IllegalArgumentException("A rota deve iniciar e terminar na origem 0.");
        }
        boolean[] visited = new boolean[n];
        visited[0] = true;
        for (int i = 1; i < n; i++) {
            int index = snapshot.get(i);
            if (index < 0 || index >= n) {
                throw new IllegalArgumentException("O índice " + index + " na posição " + i
                        + " deve estar em [0, " + n + ").");
            }
            if (index == 0) {
                throw new IllegalArgumentException("A origem 0 não pode aparecer na posição interna " + i + ".");
            }
            if (visited[index]) {
                throw new IllegalArgumentException("A cidade " + index + " está repetida na posição " + i + ".");
            }
            visited[index] = true;
        }
        // N-1 índices interiores distintos em [1, N) garantem cobertura integral.
        indices = List.copyOf(snapshot);
    }

    /** @return quantidade de cidades do ciclo, sem contar duas vezes a origem */
    public int cityCount() {
        return indices.size() - 1;
    }

    /** @return se a orientação satisfaz route[1] < route[N-1], conforme DOC 03 §6 */
    public boolean isCanonical() {
        return indices.get(1) < indices.get(cityCount() - 1);
    }

    /**
     * Obtém a orientação canônica sem ordenar as visitas ou alterar esta rota.
     * Isso não escolhe entre ciclos distintos nem prova otimalidade.
     *
     * @return esta rota, se canônica, ou o mesmo ciclo na orientação inversa
     */
    public TspRoute canonical() {
        return isCanonical() ? this : new TspRoute(indices.reversed());
    }
}
