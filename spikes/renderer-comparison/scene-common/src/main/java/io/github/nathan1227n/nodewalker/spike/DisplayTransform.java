package io.github.nathan1227n.nodewalker.spike;

/** v = R * (p - (500,0,500)); unit scale. FX rotates 180 degrees around X (Y and Z signs); both preserve handedness. */
public enum DisplayTransform {
    JAVAFX(-1, -1), JME(1, 1);
    private final int ySign, zSign;
    DisplayTransform(int ySign, int zSign) { this.ySign = ySign; this.zSign = zSign; }
    public Vec3 forward(Vec3 p) { return new Vec3(p.x() - 500, ySign * p.y(), zSign * (p.z() - 500)); }
    public Vec3 inverse(Vec3 v) { return new Vec3(v.x() + 500, ySign * v.y(), zSign * v.z() + 500); }
    public boolean reversesWinding() { return ySign * zSign < 0; }
}
