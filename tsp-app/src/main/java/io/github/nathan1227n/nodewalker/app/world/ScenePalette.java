package io.github.nathan1227n.nodewalker.app.world;

/**
 * Deliberately bounded shared materials. Emission is Phong self-illumination, not bloom/PBR.
 */
public enum ScenePalette {
    ROCK_DARK("#252c2b"), ROCK_SHALE("#353c38"), ROCK_MOSS("#474e44"), ROCK_STONE("#5d655b"),
    ROCK_RIDGE("#777e73"), ROCK_PEAK("#9aa095"),
    HORIZON_NEAR("#586268"), HORIZON_FAR("#4b5963"), HORIZON_SKY("#40535f"),
    CONCRETE("#7c7d6d"), STEEL("#424b49"), METAL("#a0a696"), PANEL("#252f31"), RUST("#785e49"),
    AMBER("#ffbe64", true), CYAN("#58dae5", true), PROVEN("#b5fff0", true), SELECTED("#e5ead1", true),
    BARK("#655b47"), NEEDLES("#243d35"), LEAVES("#4a6347"), GRASS("#7c8460"), MOSS("#354534");

    private final String color;
    private final boolean luminous;

    ScenePalette(String color) {
        this(color, false);
    }

    ScenePalette(String color, boolean luminous) {
        this.color = color;
        this.luminous = luminous;
    }

    public String color() {
        return color;
    }

    public boolean luminous() {
        return luminous;
    }

    public static ScenePalette rock(int band) {
        return values()[Math.clamp(band, 0, 8)];
    }
}
