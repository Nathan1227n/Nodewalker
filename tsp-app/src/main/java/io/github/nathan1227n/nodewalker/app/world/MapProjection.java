package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/** Orthographic logical XZ map: +X right, +Z up. Altitude is retained in the model/readout. */
public record MapProjection(double centerX, double centerZ, double spanX, double spanZ) {
    public MapProjection {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(spanX)
                || !Double.isFinite(spanZ) || spanX <= 0 || spanZ <= 0) {
            throw new IllegalArgumentException("Finite centers and positive finite spans required");
        }
    }
    public static MapProjection fit(List<Vec3> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("No points");
        double minX = Double.POSITIVE_INFINITY, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
            minZ = Math.min(minZ, point.z()); maxZ = Math.max(maxZ, point.z());
        }
        double minimum = Math.max(120, Math.max(maxX - minX, maxZ - minZ) * .2);
        return new MapProjection((minX + maxX) / 2, (minZ + maxZ) / 2,
                Math.max(minimum, maxX - minX), Math.max(minimum, maxZ - minZ));
    }

    public double scale(double width, double height, double padding) {
        return Math.max(.001, Math.min(Math.max(1, width - 2 * padding) / spanX,
                Math.max(1, height - 2 * padding) / spanZ));
    }

    public Vec3 project(Vec3 point, double width, double height, double padding) {
        double s = scale(width, height, padding);
        return new Vec3(width / 2 + (point.x() - centerX) * s,
                height / 2 + (point.z() - centerZ) * s, 0);
    }
}
