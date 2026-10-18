package io.github.nathan1227n.nodewalker.spike;

/** Shared camera in logical coordinates: same FOV (45 degrees), target and controls. */
public final class OrbitCamera {
    public static final Vec3 TARGET = new Vec3(500, 100, 500);
    private double azimuth, elevation, radius;
    public OrbitCamera() { reset(); }
    public void reset() { azimuth = -0.65; elevation = 0.60; radius = 1900; }
    public void orbit(double dx, double dy) {
        azimuth += dx * 0.005;
        elevation = Math.clamp(elevation + dy * 0.005, 0.18, 1.45);
    }
    public void zoom(double steps) { radius = Math.clamp(radius * Math.exp(-steps * 0.1), 650, 3200); }
    public void benchmark(double seconds) { azimuth = -0.65 + seconds * 0.10; elevation = 0.60; radius = 1900; }
    public Vec3 position() {
        return TARGET.add(new Vec3(Math.sin(azimuth) * Math.cos(elevation), Math.sin(elevation),
                -Math.cos(azimuth) * Math.cos(elevation)).scale(radius));
    }
}
