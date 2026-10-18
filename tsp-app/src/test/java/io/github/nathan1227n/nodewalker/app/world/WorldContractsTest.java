package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.solver.*;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class WorldContractsTest {
    static Stream<Arguments> instances() {
        return IntStream.of(3, 5, 8, 12).boxed().flatMap(n ->
                Stream.of(0L, 23L, 657L).map(seed -> Arguments.of(n, seed)));
    }

    @ParameterizedTest @MethodSource("instances")
    void exactInstanceRouteProjectionAndFit(int n, long seed) {
        TspProblem problem = TspGenerator.generate(n, seed, new GenerationOptions(10_000, 1_000_000));
        var result = new BacktrackingSolver().solve(problem, SolverExecutionOptions.nodeLimit(20_000), new SolverCancellationToken());
        var model = TspSceneModel.of(problem, Optional.of(result));
        assertSame(problem, model.problem());
        assertSame(result, model.result().orElseThrow());
        assertEquals(n, model.refuges().size());
        assertEquals(result.route().orElseThrow().indices(), model.routeIndices());
        assertEquals(n, model.segments().size());
        assertEquals(0, model.segments().getFirst().from());
        assertEquals(0, model.segments().getLast().to());
        long sum = 0;
        for (int i = 0; i < n; i++) {
            var edge = model.segments().get(i);
            assertEquals(i, edge.order());
            assertEquals(model.routeIndices().get(i).intValue(), edge.from());
            assertEquals(model.routeIndices().get(i + 1).intValue(), edge.to());
            assertEquals(model.refuges().get(edge.from()).position(), edge.geometry().start());
            assertEquals(model.refuges().get(edge.to()).position(), edge.geometry().end());
            assertEquals(problem.cost(edge.from(), edge.to()), edge.matrixCost());
            sum = Math.addExact(sum, edge.matrixCost());
            assertVector(problem.cities().get(i), model.transform().toLogical(model.refuges().get(i).position()));
        }
        assertEquals(RouteValidator.validateAndCost(problem, result.route().orElseThrow()), sum);
        assertEquals(sum, model.cost().orElseThrow());
        assertEquals(result.optimalityGuaranteed(), model.certified());
        assertSame(result.metrics(), model.metrics());
        assertCameraFit(model, 1200, 650);
        assertCameraFit(model, 340, 900);
        for (Vec3 position : model.positions()) {
            Vec3 point = model.projection().project(position, 800, 500, 60);
            assertTrue(point.x() >= 59 && point.x() <= 741);
            assertTrue(point.y() >= 59 && point.y() <= 441);
        }
    }

    @Test void fullIntegerExtremesAndDegenerateBoundsAreReversible() {
        for (List<City> cities : List.of(
                List.of(new City(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0),
                        new City(1, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        new City(2, 0, 0, Integer.MIN_VALUE)),
                List.of(new City(0, 7, 7, 7), new City(1, 7, 7, 7), new City(2, 7, 7, 7)),
                List.of(new City(0, 0, -5, 0), new City(1, 0, 0, 0), new City(2, 0, 5, 0)))) {
            var model = TspSceneModel.of(new TspProblem(cities, new long[3][3]), Optional.empty());
            for (var refuge : model.refuges()) assertVector(refuge.logical(), model.transform().toLogical(refuge.position()));
            assertCameraFit(model, 400, 800);
            for (var p : model.positions()) assertTrue(Double.isFinite(model.projection().project(p, 1, 1, 60).x()));
            for (var p : model.terrain(false).vertices()) assertTrue(Double.isFinite(p.y()));
        }
    }

    @ParameterizedTest @EnumSource(value = SolverStatus.class, names = {"CONCLUIDO", "CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void terminalTruthAndMissingMetrics(SolverStatus status) {
        var problem = zeroProblem();
        var route = new TspRoute(List.of(0, 1, 2, 0));
        var result = status == SolverStatus.CONCLUIDO ? TspResult.completed(problem, route, 0, SolverMetrics.empty())
                : TspResult.interrupted(problem, status, Optional.of(route), OptionalLong.of(0), SolverMetrics.empty(), "real reason");
        var model = TspSceneModel.of(problem, Optional.of(result));
        assertEquals(status == SolverStatus.CONCLUIDO, model.certified());
        assertEquals(status.name(), model.statusLabel());
        assertEquals(0, model.cost().orElseThrow());
        assertTrue(model.metrics().elapsedNanos().isEmpty());
        assertTrue(model.metrics().visitedNodes().isEmpty());
        assertTrue(model.metrics().discardedRoutes().isEmpty());
        assertTrue(model.metrics().completeRoutesEvaluated().isEmpty());
        if (!model.certified()) assertTrue(model.routeLabel().contains("NÃO ÓTIMO CERTIFICADO"));
        for (var segment : model.segments()) assertEquals(0, segment.geometry().length());
    }

    @ParameterizedTest @EnumSource(value = SolverStatus.class, names = {"CANCELADO", "LIMITE_ATINGIDO", "ERRO"})
    void noIncumbentMeansNoRoute(SolverStatus status) {
        var p = zeroProblem();
        var result = TspResult.interrupted(p, status, Optional.empty(), OptionalLong.empty(), SolverMetrics.empty(), "reason");
        var model = TspSceneModel.of(p, Optional.of(result));
        assertTrue(model.segments().isEmpty());
        assertTrue(model.routeIndices().isEmpty());
        assertTrue(model.cost().isEmpty());
        assertFalse(model.certified());
    }

    @Test void maximumLongCostIsLegitimateAndGeometryDoesNotDefineCost() {
        var p = new TspProblem(zeroProblem().cities(), new long[][]{{0, Long.MAX_VALUE, 0}, {Long.MAX_VALUE, 0, 0}, {0, 0, 0}});
        var r = new TspRoute(List.of(0, 1, 2, 0));
        var model = TspSceneModel.of(p, Optional.of(TspResult.completed(p, r, Long.MAX_VALUE, SolverMetrics.empty())));
        assertEquals(Long.MAX_VALUE, model.cost().orElseThrow());
        assertEquals(Long.MAX_VALUE, model.segments().getFirst().matrixCost());
        assertTrue(model.segments().stream().allMatch(s -> s.geometry().length() == 0));
    }

    @Test void suppliedInterruptedOrientationIsNotCanonicalized() {
        var p = zeroProblem();
        var r = new TspRoute(List.of(0, 2, 1, 0));
        var model = TspSceneModel.of(p, Optional.of(TspResult.interrupted(p, SolverStatus.CANCELADO,
                Optional.of(r), OptionalLong.of(0), SolverMetrics.empty(), "cancelled")));
        assertEquals(r.indices(), model.routeIndices());
    }

    @Test void incompatibleResultIsRejectedAndUpdatesShareTerrain() {
        var p = zeroProblem();
        var model = TspSceneModel.of(p, Optional.empty());
        assertEquals("SEM RESULTADO", model.statusLabel());
        var result = TspResult.completed(p, new TspRoute(List.of(0, 1, 2, 0)), 0, SolverMetrics.empty());
        assertSame(model.terrain(false), model.withResult(Optional.of(result)).terrain(false));
        var other = new TspProblem(p.cities(), new long[][]{{0, 1, 1}, {1, 0, 1}, {1, 1, 0}});
        assertThrows(IllegalArgumentException.class, () -> TspSceneModel.of(other, Optional.of(result)));
        assertThrows(UnsupportedOperationException.class, () -> model.refuges().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.terrain(false).vertices().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.withResult(Optional.of(result)).segments().clear());
    }

    @Test void exactNodeBoundedN12HasAnHonestIncumbent() {
        var p = TspGenerator.generate(12, 23, new GenerationOptions(1000, 1_000_000));
        var result = new BacktrackingSolver().solve(p, SolverExecutionOptions.nodeLimit(12), new SolverCancellationToken());
        var model = TspSceneModel.of(p, Optional.of(result));
        assertEquals(SolverStatus.LIMITE_ATINGIDO, result.status());
        assertEquals(12, model.segments().size());
        assertFalse(model.certified());
        assertEquals(12, model.metrics().visitedNodes().orElseThrow());
    }

    @Test void zeroOppositeAndParallelSegmentsHaveFiniteOrientations() {
        for (Vec3 end : List.of(Vec3.ZERO, new Vec3(0, -3, 0), new Vec3(0, 3, 0), new Vec3(1, 2, -4))) {
            var g = new SegmentGeometry(Vec3.ZERO, end);
            assertEquals(1, g.rotationAxis().length(), 1e-12);
            assertTrue(Double.isFinite(g.rotationDegrees()));
            assertEquals(end.length(), g.length());
        }
    }

    @Test void terrainBudgetsAndFacesAreStable() {
        var model = TspSceneModel.of(zeroProblem(), Optional.empty());
        assertEquals(12_800, model.terrain(false).faces().size());
        assertEquals(2048, model.terrain(true).faces().size());
        for (boolean simple : List.of(false, true)) {
            var terrain = model.terrain(simple);
            for (var face : terrain.faces()) {
                assertTrue(face.a() >= 0 && face.a() < terrain.vertices().size());
                assertTrue(face.b() >= 0 && face.b() < terrain.vertices().size());
                assertTrue(face.c() >= 0 && face.c() < terrain.vertices().size());
                Vec3 normal = terrain.vertices().get(face.b()).subtract(terrain.vertices().get(face.a()))
                        .cross(terrain.vertices().get(face.c()).subtract(terrain.vertices().get(face.a())));
                assertTrue(normal.y() < 0, "Up-facing winding");
            }
        }
    }

    @Test void optionalConnectionsAreBoundedAndExcludeRoute() {
        var p = TspGenerator.generate(50, 23, new GenerationOptions(10_000, 1_000_000));
        var model = TspSceneModel.of(p, Optional.empty());
        assertEquals(32, model.secondaryConnections(0).size());
        assertTrue(model.secondaryConnections(0).stream().allMatch(e -> e.from() == 0 && e.to() != 0));
    }

    static TspProblem zeroProblem() {
        return new TspProblem(List.of(new City(0, 0, 0, 0), new City(1, 0, 0, 0), new City(2, 0, 0, 0)), new long[3][3]);
    }

    private static void assertCameraFit(TspSceneModel model, double width, double height) {
        var camera = new OrbitCamera(model.positions(), model.terrain(false).extent());
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) camera.top();
            var pose = camera.pose(width, height);
            assertEquals(0, pose.right().dot(pose.forward()), 1e-12);
            assertEquals(0, pose.down().dot(pose.forward()), 1e-12);
            assertEquals(1, pose.down().length(), 1e-12);
            for (Vec3 p : model.positions()) {
                Vec3 projected = pose.project(p, width, height);
                assertTrue(projected.x() > 0 && projected.x() < width, () -> "X fit: " + projected);
                assertTrue(projected.y() > 0 && projected.y() < height, () -> "Y fit: " + projected);
                assertTrue(projected.z() > pose.nearClip() && projected.z() < pose.farClip());
            }
        }
    }
    private static void assertVector(City expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-5);
        assertEquals(expected.y(), actual.y(), 1e-5);
        assertEquals(expected.z(), actual.z(), 1e-5);
    }
}
