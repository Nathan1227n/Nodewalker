package io.github.nathan1227n.nodewalker.app.world;

public enum EdgeWeightMode {
    NONE("Sem custos"), SOLUTION("Custos da solução"), CONNECTIONS("Custos das conexões");
    private final String label;
    EdgeWeightMode(String label){this.label=label;}
    @Override public String toString(){return label;}
}
