package io.github.nathan1227n.nodewalker.app.world;

/**
 * Deterministic presentation clock, not a search trace or a solver progress estimate.
 * Time is supplied by the view; headless tests advance it without sleeps/toolkit.
 */
public final class WorldPlayback {
    public enum Stage { REFUGES, PATH, FINISHED }
    private int cities, edges;
    private double cityProgress, edgeProgress, speed = 1;
    private boolean paused;

    public void newWorld(int count) {
        if (count < 1) throw new IllegalArgumentException("Missing cities");
        cities = count; cityProgress = 0; edges = 0; edgeProgress = 0; paused = false;
    }
    public void path(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative edge count");
        edges = count; edgeProgress = 0; paused = false;
    }
    public void advance(long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("Negative presentation duration");
        if (paused) return;
        double remaining = nanos * speed;
        double cityStep = Math.clamp(cities * 250_000_000d, 1_500_000_000d, 3_000_000_000d) / Math.max(1, cities);
        if (cityProgress < cities) {
            double consumed = Math.min(remaining, (cities - cityProgress) * cityStep);
            cityProgress = snap(cityProgress + consumed / cityStep, cities);
            remaining -= consumed;
        }
        if (cityProgress >= cities && edges > 0)
            edgeProgress = snap(edgeProgress + remaining / (Math.clamp(edges * 800_000_000d, 2_400_000_000d, 10_000_000_000d) / edges), edges);
    }
    private static double snap(double value, int count) {
        double integer = Math.rint(value);
        if (Math.abs(value - integer) < 1e-9) value = integer;
        return Math.min(count, value);
    }
    public Stage stage() {
        return cityProgress < cities ? Stage.REFUGES : edgeProgress < edges ? Stage.PATH : Stage.FINISHED;
    }
    public boolean active() { return stage() != Stage.FINISHED; }
    public boolean paused() { return paused; }
    public void togglePause() { if (active()) paused = !paused; }
    public double speed() { return speed; }
    public void speed(double next) {
        if (!Double.isFinite(next) || next < .25 || next > 4) throw new IllegalArgumentException("Speed must be .25x…4x");
        speed = next;
    }
    public double cityFraction(int index) { java.util.Objects.checkIndex(index, cities); return Math.clamp(cityProgress - index, 0, 1); }
    public double edgeFraction(int index) { java.util.Objects.checkIndex(index, edges); return Math.clamp(edgeProgress - index, 0, 1); }
    public int completedCities() { return (int) cityProgress; }
    public int completedEdges() { return (int) edgeProgress; }
    public int edgeCount() { return edges; }
    public void revealCities() { cityProgress = cities; }
    public void finish() { revealCities(); edgeProgress = edges; paused = false; }
    public void replay() { if (edges > 0) { revealCities(); edgeProgress = 0; paused = false; } }
    public void clear() { cities = 0; edges = 0; cityProgress = 0; edgeProgress = 0; paused = false; }
    public String label() {
        return switch (stage()) {
            case REFUGES -> (paused ? "Pausado · " : "Revelando refúgios · ") + completedCities() + "/" + cities;
            case PATH -> (paused ? "Pausado · " : "Animando caminho calculado · ") + completedEdges() + "/" + edges + " ligações";
            case FINISHED -> edges > 0 ? "Caminho calculado · animação concluída" : "Refúgios prontos";
        };
    }
}
