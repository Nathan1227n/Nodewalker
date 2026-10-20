package io.github.nathan1227n.nodewalker.app.world;

/** Exact endpoints plus a stable cylinder orientation, including coincident and antiparallel ends. */
public record SegmentGeometry(Vec3 start, Vec3 end) {
    public Vec3 pointAt(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) throw new IllegalArgumentException("Fraction must be 0…1");
        if (fraction == 0) return start;
        if (fraction == 1) return end;
        return start.add(end.subtract(start).multiply(fraction));
    }
    public Vec3 midpoint() { return start.add(end).multiply(.5); }
    public double length() { return end.subtract(start).length(); }
    public Vec3 rotationAxis() {
        Vec3 cross = new Vec3(0, 1, 0).cross(end.subtract(start).unit());
        return cross.length() < 1e-12 ? new Vec3(1, 0, 0) : cross.unit();
    }
    public double rotationDegrees() {
        if (length() == 0) return 0;
        return Math.toDegrees(Math.acos(Math.clamp((end.y() - start.y()) / length(), -1, 1)));
    }
}
