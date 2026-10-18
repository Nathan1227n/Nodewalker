package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/**
 * World-anchored dominant-plane UVs. Steep walls use height instead of stretching an XZ projection.
 */
public final class SurfaceMapping {
    private SurfaceMapping() {
    }

    public record UV(double u, double v) {
    }

    public static List<UV> triangle(Vec3 a, Vec3 b, Vec3 c, double tileSize) {
        if (!Double.isFinite(tileSize) || tileSize <= 0)
            throw new IllegalArgumentException("Positive tile size required");
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        int axis = Math.abs(normal.y()) >= Math.max(Math.abs(normal.x()), Math.abs(normal.z())) ? 1
                : Math.abs(normal.x()) >= Math.abs(normal.z()) ? 0 : 2;
        return List.of(project(a, axis, tileSize), project(b, axis, tileSize), project(c, axis, tileSize));
    }

    private static UV project(Vec3 p, int axis, double tile) {
        return switch (axis) {
            case 0 -> new UV(p.z() / tile, -p.y() / tile);
            case 2 -> new UV(p.x() / tile, -p.y() / tile);
            default -> new UV(p.x() / tile, p.z() / tile);
        };
    }
}
