package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.City;
import java.util.List;

/** Uniform scale and a proper rotation (Y/Z signs) from logical XYZ to JavaFX XYZ. */
public record WorldTransform(Vec3 center, double scale) {
    public WorldTransform {
        if (center == null || !Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("A finite positive scale and center are required");
        }
    }

    public static WorldTransform fit(List<City> cities) {
        if (cities.isEmpty()) throw new IllegalArgumentException("No cities");
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (City city : cities) {
            minX = Math.min(minX, city.x()); maxX = Math.max(maxX, city.x());
            minY = Math.min(minY, city.y()); maxY = Math.max(maxY, city.y());
            minZ = Math.min(minZ, city.z()); maxZ = Math.max(maxZ, city.z());
        }
        double span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        return new WorldTransform(new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2),
                span == 0 ? 1 : 1000 / span);
    }

    public Vec3 toVisual(City city) { return toVisual(new Vec3(city.x(), city.y(), city.z())); }
    public Vec3 toVisual(Vec3 logical) {
        Vec3 p = logical.subtract(center);
        return new Vec3(p.x() * scale, -p.y() * scale, -p.z() * scale);
    }
    public Vec3 toLogical(Vec3 visual) {
        return center.add(new Vec3(visual.x() / scale, -visual.y() / scale, -visual.z() / scale));
    }
}
