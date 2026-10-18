package io.github.nathan1227n.nodewalker.app.world.render;

import java.util.Objects;

import javafx.application.Platform;
import javafx.scene.image.Image;

/**
 * Shared immutable image; loaded lazily on FX thread, including in the independent 2D renderer.
 */
final class WorldTextures {
    private static Image rock;
    private static Image foliage;

    private WorldTextures() {
    }

    static Image rock() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Load textures on FX thread");
        if (rock == null) {
            String url = Objects.requireNonNull(WorldTextures.class.getResource("/textures/basalt-slate-albedo.png")).toExternalForm();
            rock = new Image(url, 1024, 1024, false, true, false);
            if (rock.isError())
                throw new IllegalStateException("Cannot load bundled terrain texture", rock.getException());
        }
        return rock;
    }

    static Image foliage() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Load textures on FX thread");
        if (foliage == null) {
            String url = Objects.requireNonNull(WorldTextures.class.getResource("/textures/weathered-evergreen-albedo.png")).toExternalForm();
            foliage = new Image(url, 512, 512, false, true, false);
            if (foliage.isError())
                throw new IllegalStateException("Cannot load foliage texture", foliage.getException());
        }
        return foliage;
    }
}
