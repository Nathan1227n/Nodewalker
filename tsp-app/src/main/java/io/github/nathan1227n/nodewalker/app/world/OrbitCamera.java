package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/** Pure camera state shared across renderer replacement; no timer or JavaFX objects. */
public final class OrbitCamera {
    private final double sceneRadius;
    private double azimuth = -.65;
    private double elevation = .63;
    private double zoom = 1;
    private Vec3 target = Vec3.ZERO;

    public OrbitCamera(List<Vec3> points, double terrainExtent) {
        sceneRadius = Math.max(500, Math.max(terrainExtent * .96,
                points.stream().mapToDouble(Vec3::length).max().orElse(0) + 120));
    }

    public void orbit(double dx, double dy) {
        azimuth = Math.IEEEremainder(azimuth - dx * .006, Math.PI * 2);
        elevation = Math.clamp(elevation + dy * .005, .12, Math.PI / 2);
    }
    public void zoom(double scroll) { zoom = Math.clamp(zoom * Math.exp(-Math.clamp(scroll, -400, 400) * .0015), .32, 2.8); }
    public void reset() { azimuth = -.65; elevation = .63; zoom = 1; target = Vec3.ZERO; }
    public void top() { elevation = Math.PI / 2; azimuth = 0; target = Vec3.ZERO; zoom = 1; }
    public void focus(Vec3 point) { target = point; zoom = .65; }

    public Pose pose(double width, double height) {
        double verticalFov = Math.toRadians(38);
        double aspect = Math.max(1, width) / Math.max(1, height);
        double halfFov = Math.min(verticalFov / 2, Math.atan(Math.tan(verticalFov / 2) * aspect));
        double fit = sceneRadius / Math.sin(halfFov);
        double radius = fit * zoom;
        Vec3 offset = new Vec3(Math.sin(azimuth) * Math.cos(elevation), -Math.sin(elevation),
                -Math.cos(azimuth) * Math.cos(elevation)).multiply(radius);
        Vec3 eye = target.add(offset);
        Vec3 forward = target.subtract(eye).unit();
        // An analytic right vector remains defined even at the exact top view.
        Vec3 right = new Vec3(Math.cos(azimuth), 0, Math.sin(azimuth));
        Vec3 down = forward.cross(right).unit();
        return new Pose(eye, right, down, forward, Math.max(.1, radius / 10000),
                radius + sceneRadius * 5 + target.length(), radius);
    }

    public record Pose(Vec3 eye, Vec3 right, Vec3 down, Vec3 forward, double nearClip, double farClip, double distance) {
        public Vec3 project(Vec3 world, double width, double height) {
            Vec3 relative = world.subtract(eye);
            double depth = relative.dot(forward);
            if (depth <= nearClip) return new Vec3(-10000, -10000, 0);
            double focal = height / (2 * Math.tan(Math.toRadians(19)));
            return new Vec3(width / 2 + relative.dot(right) * focal / depth,
                    height / 2 + relative.dot(down) * focal / depth, depth);
        }
    }
}
