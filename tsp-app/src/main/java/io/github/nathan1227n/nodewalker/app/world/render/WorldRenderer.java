package io.github.nathan1227n.nodewalker.app.world.render;

import javafx.scene.layout.Region;

interface WorldRenderer extends AutoCloseable {
    Region node();
    void refresh();
    default void resultChanged() { refresh(); }
    @Override void close();
}
