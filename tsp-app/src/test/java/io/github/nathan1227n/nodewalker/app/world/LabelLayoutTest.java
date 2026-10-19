package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LabelLayoutTest {
    @ParameterizedTest
    @ValueSource(ints = {3, 5, 8, 12, 60})
    void coincidentPointsRetainBaseAndSelectionWithoutCollisions(int n) {
        var requests = IntStream.range(0, n).mapToObj(i -> new LabelLayout.Request(i, new Vec3(180, 130, 10), i == 0 || i == n - 1 ? 100 : 34, 24, true)).toList();
        var viewport = new LabelLayout.Rect(8, 68, 320, 200);
        var result = LabelLayout.arrange(requests, n - 1, viewport, false);
        assertTrue(result.stream().anyMatch(p -> p.index() == 0));
        assertTrue(result.stream().anyMatch(p -> p.index() == n - 1));
        assertEquals(n - 1, result.getFirst().index());
        for (var p : result) {
            assertEquals(requests.get(p.index()).anchor(), p.anchor());
            assertTrue(viewport.contains(p.bounds()));
            for (var q : result) if (q.index() != p.index()) assertFalse(p.bounds().overlaps(q.bounds(), 4));
        }
        var reversed = new ArrayList<>(requests);
        Collections.reverse(reversed);
        assertEquals(result, LabelLayout.arrange(reversed, n - 1, viewport, false), "No incidental map/iteration order");
        var reduced = LabelLayout.arrange(requests, n - 1, viewport, true);
        assertEquals(2, reduced.size());
        assertTrue(reduced.stream().allMatch(p -> p.index() == 0 || p.index() == n - 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 12})
    void viewportEdgesAndHiddenAnchorsAreHandled(int n) {
        var requests = new ArrayList<LabelLayout.Request>();
        for (int i = 0; i < n; i++)
            requests.add(new LabelLayout.Request(i, new Vec3(i % 2 == 0 ? 0 : 799, i % 3 == 0 ? 0 : 599, 10), 80, 24, i != 3));
        var v = new LabelLayout.Rect(8, 68, 784, 490);
        var result = LabelLayout.arrange(requests, 1, v, false);
        assertTrue(result.stream().noneMatch(p -> p.index() == 3));
        for (var p : result) assertTrue(v.contains(p.bounds()));
        assertTrue(LabelLayout.arrange(requests, 1, new LabelLayout.Rect(0, 0, 0, 0), false).isEmpty());
    }
}
