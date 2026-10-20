package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;
import java.util.Objects;

/** O(1) presentation invalidation: shares the world, exposes neither old results nor topology. */
public record PendingSceneModel(PreparedWorld preparedWorld) implements WorldSceneModel {
    public PendingSceneModel { Objects.requireNonNull(preparedWorld); }
    @Override public List<Edge> segments() { return List.of(); }
    @Override public List<Edge> secondaryConnections(int selected) { return List.of(); }
    @Override public boolean solidPath() { return false; }
}
