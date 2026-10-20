package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Screen-space allocation, with explicit priority and deterministic tie breaking. Never moves an anchor.
 */
public final class LabelLayout {
    private LabelLayout() {
    }

    public record Rect(double x, double y, double width, double height) {
        public Rect {
            if (!Double.isFinite(x + y + width + height) || width < 0 || height < 0)
                throw new IllegalArgumentException("Invalid rectangle");
        }

        public boolean contains(Rect b) {
            return b.x >= x && b.y >= y && b.x + b.width <= x + width && b.y + b.height <= y + height;
        }

        public boolean overlaps(Rect b, double gap) {
            return x < b.x + b.width + gap && x + width + gap > b.x && y < b.y + b.height + gap && y + height + gap > b.y;
        }
    }

    public record Request(int index, Vec3 anchor, double width, double height, boolean visible) {
    }

    public record Placement(int index, Vec3 anchor, Rect bounds) {
    }

    public static List<Placement> arrange(List<Request> requests, int selected, Rect viewport, boolean reduced) {
        return arrange(requests, selected, viewport, reduced, java.util.Set.of(0));
    }

    public static List<Placement> arrange(List<Request> requests, int selected, Rect viewport, boolean reduced, java.util.Set<Integer> priority) {
        var ordered = requests.stream().filter(Request::visible)
                .filter(r -> !reduced || r.index == selected || priority.contains(r.index))
                .sorted(Comparator.comparingInt((Request r) -> r.index == selected ? 0 : priority.contains(r.index) ? 1 : 2)
                        .thenComparingInt(Request::index)).toList();
        var placed = new ArrayList<Placement>();
        for (Request r : ordered) {
            if (placed.size() >= 48) break;
            if (r.width > viewport.width || r.height > viewport.height) continue;
            Rect slot = null;
            double[][] offsets = {{12, -30}, {-r.width - 12, -30}, {12, 12}, {-r.width - 12, 12},
                    {12, -60}, {-r.width - 12, -60}, {12, 42}, {-r.width - 12, 42}};
            for (double[] offset : offsets) {
                Rect candidate = clamp(r.anchor.x() + offset[0], r.anchor.y() + offset[1], r, viewport);
                if (free(candidate, placed)) {
                    slot = candidate;
                    break;
                }
            }
            if (slot == null && (r.index == selected || priority.contains(r.index))) {
                // Priority labels can occupy a free edge slot, with a leader to the unchanged point.
                for (double y = viewport.y; y <= viewport.y + viewport.height - r.height && slot == null; y += r.height + 5) {
                    for (double x : new double[]{viewport.x, viewport.x + viewport.width - r.width}) {
                        Rect candidate = new Rect(x, y, r.width, r.height);
                        if (free(candidate, placed)) {
                            slot = candidate;
                            break;
                        }
                    }
                }
            }
            if (slot != null) placed.add(new Placement(r.index, r.anchor, slot));
        }
        return List.copyOf(placed);
    }

    private static Rect clamp(double x, double y, Request r, Rect v) {
        return new Rect(Math.clamp(x, v.x, v.x + v.width - r.width), Math.clamp(y, v.y, v.y + v.height - r.height), r.width, r.height);
    }

    private static boolean free(Rect candidate, List<Placement> placed) {
        return placed.stream().noneMatch(p -> p.bounds.overlaps(candidate, 5));
    }
}
