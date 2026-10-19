package io.github.nathan1227n.nodewalker.core.shortestpath;

import java.util.concurrent.atomic.AtomicBoolean;

/** Persistent, thread-safe request. Use a new token for each independent shortest-path run. */
public final class ShortestPathCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel() { cancelled.set(true); }
    public boolean isCancellationRequested() { return cancelled.get(); }
}
