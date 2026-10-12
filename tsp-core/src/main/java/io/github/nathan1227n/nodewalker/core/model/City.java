package io.github.nathan1227n.nodewalker.core.model;

/**
 * Cidade imutável, identificada por um índice não negativo e uma posição 3D.
 * As coordenadas são inteiras e podem ocupar todo o intervalo de {@code int}.
 * O intervalo [0, 1000] e a distância mínima são regras do gerador, não do modelo.
 * A posição não determina custos: uma instância pode usar uma matriz manual.
 *
 * @param index índice da cidade; em um {@link TspProblem}, coincide com sua posição na lista
 * @param x coordenada inteira no eixo X
 * @param y coordenada inteira no eixo Y
 * @param z coordenada inteira no eixo Z
 */
public record City(int index, int x, int y, int z) {

    /**
     * @throws IllegalArgumentException se o índice for negativo
     */
    public City {
        if (index < 0) {
            throw new IllegalArgumentException("O índice da cidade deve ser não negativo: " + index);
        }
    }
}
