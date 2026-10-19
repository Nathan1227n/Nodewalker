package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class WorldGeometryTest {
    @Test
    void terrainRemainsContinuousAcrossLocalFoundationRegion() {
        var anchors = List.of(new Vec3(0, 500, 0), new Vec3(300, -500, 0), new Vec3(0, -500, 300));
        double before = TerrainFactory.heightAt(anchors, 240 - 1e-5, 0);
        double after = TerrainFactory.heightAt(anchors, 240 + 1e-5, 0);
        assertEquals(before, after, .001, "Decorative adaptation must not introduce an artificial cliff");
    }

    @ParameterizedTest
    @EnumSource(DemoCatalog.class)
    void architectureHorizonAndBudgetsPreserveEveryAnchor(DemoCatalog demo) {
        var problem = TspGenerator.generate(demo.cityCount(), demo.seed(), new GenerationOptions(10000, 1000000));
        var model = TspSceneModel.of(problem, Optional.empty());
        for (boolean simple : new boolean[]{false, true}) {
            var world = model.geometry(simple);
            assertEquals(problem.size(), world.refuges().size());
            assertTrue(world.horizon().extent() > world.terrain().extent() * 3);
            assertTrue(world.triangleCount() < (simple ? 12000 : 48000), () -> "Triangle budget: " + world.triangleCount());
            assertTrue(world.numericBufferBytes() < 32 * 1024 * 1024);
            assertTrue(world.decoration().sites().size() <= 32);
            assertTrue(world.vegetation().plants().size() <= 384);
            assertFalse(world.vegetation().plants().isEmpty());
            var plantIds = new HashSet<Integer>();
            for (var plant : world.vegetation().plants()) {
                assertTrue(plantIds.add(plant.id()));
                for (var p : model.positions())
                    assertTrue(Math.hypot(plant.position().x() - p.x(), plant.position().z() - p.z()) >= 120);
                assertTrue(plant.height() > 0 && Double.isFinite(plant.height()));
                var point = plant.position();
                var terrain = world.terrain();
                if (Math.abs(point.x()) < terrain.extent() && Math.abs(point.z()) < terrain.extent()) {
                    int cells = simple ? 32 : 80;
                    double gx = (point.x() + terrain.extent()) / (2 * terrain.extent()) * cells;
                    double gz = (point.z() + terrain.extent()) / (2 * terrain.extent()) * cells;
                    int ix = (int) gx, iz = (int) gz;
                    var face = terrain.faces().get(2 * (iz * cells + ix) + (gx - ix >= gz - iz ? 0 : 1));
                    var a = terrain.vertices().get(face.a());
                    var normal = terrain.vertices().get(face.b()).subtract(a).cross(terrain.vertices().get(face.c()).subtract(a)).unit();
                    assertEquals(0, point.subtract(a).dot(normal), 1e-8, "Vegetation must touch the actual rendered terrain");
                }
            }
            for (var site : world.decoration().sites())
                for (var p : model.positions())
                    assertTrue(Math.hypot(site.position().x() - p.x(), site.position().z() - p.z()) >= 120);
            var materials = new HashSet<ScenePalette>();
            for (int i = 0; i < problem.size(); i++) {
                var refuge = world.refuges().get(i);
                assertEquals(i, refuge.index());
                assertEquals(model.positions().get(i), refuge.anchor());
                assertEquals(i == 0 ? RefugeGeometryFactory.Variant.CENTRAL : RefugeGeometryFactory.Variant.values()[1 + (i - 1) % 3], refuge.variant());
            }
            for (var mesh : world.meshes()) {
                materials.add(mesh.material());
                for (var f : mesh.triangles())
                    for (int i : new int[]{f.a(), f.b(), f.c()}) assertTrue(i >= 0 && i < mesh.vertices().size());
            }
            assertTrue(materials.size() <= 20);
            for (var t : new TerrainData[]{world.terrain(), world.horizon()}) {
                assertEquals(t.vertices().size(), t.normals().size());
                for (var normal : t.normals()) {
                    assertEquals(1, normal.length(), 1e-12);
                    assertTrue(normal.y() < 0);
                }
            }
            // Exact boundary reuse avoids cracks between central terrain and distant geography.
            var boundary = new HashSet<>(world.horizon().vertices().subList(0, simple ? 128 : 320));
            var boundaryNormals = new java.util.HashMap<Vec3, Vec3>();
            for (int i = 0; i < (simple ? 128 : 320); i++)
                boundaryNormals.put(world.horizon().vertices().get(i), world.horizon().normals().get(i));
            for (var p : world.terrain().vertices())
                if (Math.abs(p.x()) == world.terrain().extent() || Math.abs(p.z()) == world.terrain().extent()) {
                    assertTrue(boundary.contains(p));
                    int at = world.terrain().vertices().indexOf(p);
                    assertEquals(boundaryNormals.get(p), world.terrain().normals().get(at), "Different mesh resolutions must not create a lighting seam");
                }
        }
        var detailedPlants = model.geometry(false).vegetation().plants().stream().collect(java.util.stream.Collectors.toMap(VegetationFactory.Plant::id, p -> p));
        for (var plant : model.geometry(true).vegetation().plants()) {
            var full = detailedPlants.get(plant.id());
            assertNotNull(full);
            assertEquals(full.kind(), plant.kind());
            assertEquals(full.height(), plant.height());
            assertEquals(full.position().x(), plant.position().x());
            assertEquals(full.position().z(), plant.position().z());
        }
        assertThrows(UnsupportedOperationException.class, () -> model.geometry(false).vegetation().plants().clear());
        assertEquals(model.geometry(false), TspSceneModel.of(problem, Optional.empty()).geometry(false));
    }
}
