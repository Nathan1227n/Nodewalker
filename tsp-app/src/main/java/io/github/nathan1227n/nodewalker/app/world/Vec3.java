package io.github.nathan1227n.nodewalker.app.world;

/** Immutable, finite visual coordinates. No JavaFX dependency. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
    }

    public Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
    public Vec3 subtract(Vec3 other) { return new Vec3(x - other.x, y - other.y, z - other.z); }
    public Vec3 multiply(double scalar) { return new Vec3(x * scalar, y * scalar, z * scalar); }
    public double dot(Vec3 other) { return x * other.x + y * other.y + z * other.z; }
    public Vec3 cross(Vec3 b) { return new Vec3(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
    public double length() { return Math.hypot(Math.hypot(x, y), z); }
    public Vec3 unit() {
        double length = length();
        return length == 0 ? ZERO : multiply(1 / length);
    }
}
