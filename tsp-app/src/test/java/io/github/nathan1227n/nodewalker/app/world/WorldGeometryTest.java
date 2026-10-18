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
            for (var p : world.terrain().vertices())
                if (Math.abs(p.x()) == world.terrain().extent() || Math.abs(p.z()) == world.terrain().extent())
                    assertTrue(boundary.contains(p));
        }
        assertEquals(model.geometry(false), TspSceneModel.of(problem, Optional.empty()).geometry(false));
    }
}
