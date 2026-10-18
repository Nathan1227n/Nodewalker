package io.github.nathan1227n.nodewalker.spike;

/** Engine-independent logical/display vector. */
public record Vec3(double x, double y, double z) {
    public Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
    public Vec3 subtract(Vec3 b) { return new Vec3(x - b.x, y - b.y, z - b.z); }
    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
}
