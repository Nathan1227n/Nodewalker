package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceMappingTest {
    @Test
    void steepAndHorizontalFacesKeepTwoDimensionalTextureArea() {
        var origin = new Vec3(-80, -40, -20);
        for (var pair : List.of(
                List.of(new Vec3(60, 0, 0), new Vec3(0, 0, 90)),
                List.of(new Vec3(60, 0, 0), new Vec3(0, 90, 0)),
                List.of(new Vec3(0, 60, 0), new Vec3(0, 0, 90)),
                List.of(new Vec3(2, -600, 1), new Vec3(100, -30, 80)))) {
            Vec3 b = origin.add(pair.get(0)), c = origin.add(pair.get(1));
            var uv = SurfaceMapping.triangle(origin, b, c, 240);
            double area = Math.abs((uv.get(1).u() - uv.get(0).u()) * (uv.get(2).v() - uv.get(0).v())
                    - (uv.get(1).v() - uv.get(0).v()) * (uv.get(2).u() - uv.get(0).u()));
            double spatial = b.subtract(origin).cross(c.subtract(origin)).length();
            assertTrue(area >= spatial / (Math.sqrt(3) * 240 * 240) - 1e-12, "Texture projection collapsed on a steep wall");
            var reversed = SurfaceMapping.triangle(c, b, origin, 240);
            assertEquals(List.of(uv.get(2), uv.get(1), uv.get(0)), reversed);
        }
    }

    @Test
    void coincidentVerticesAndInvalidTileSizesAreSafe() {
        var uv = SurfaceMapping.triangle(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 240);
        assertTrue(uv.stream().allMatch(p -> Double.isFinite(p.u()) && Double.isFinite(p.v())));
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> SurfaceMapping.triangle(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, invalid));
    }

    @Test
    void textureCoordinatesRemainAnchoredWhenAnAdjacentFaceChangesSize() {
        Vec3 a = new Vec3(-240, 12, 480), b = new Vec3(0, 12, 480);
        var first = SurfaceMapping.triangle(a, b, new Vec3(-240, 12, 720), 240);
        var larger = SurfaceMapping.triangle(a, b, new Vec3(-240, 12, 2400), 240);
        assertEquals(first.subList(0, 2), larger.subList(0, 2));
        assertNotEquals(first.get(0), first.get(1));
    }

    @ParameterizedTest
    @ValueSource(strings={"basalt-slate-albedo.png","weathered-evergreen-albedo.png"})
    void bundledTexturesDecodeWithoutGraphicsAndHaveSurfaceDetail(String asset) throws Exception {
        try (var stream = getClass().getResourceAsStream("/textures/"+asset)) {
            assertNotNull(stream, "Bundled texture must also be in the packaged app");
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(image.getWidth(), image.getHeight());
            assertTrue(image.getWidth() >= 512 && image.getWidth() <= 2048);
            int min = 255, max = 0;
            for (int y = 0; y < image.getHeight(); y += 17)
                for (int x = 0; x < image.getWidth(); x += 17) {
                    int rgb = image.getRGB(x, y), luma = (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
                    min = Math.min(min, luma);
                    max = Math.max(max, luma);
                }
            assertTrue(max - min > 40, "A flat placeholder is not a rock texture");
        }
    }
}
