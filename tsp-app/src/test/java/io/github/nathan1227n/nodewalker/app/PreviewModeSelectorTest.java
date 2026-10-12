
package io.github.nathan1227n.nodewalker.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewModeSelectorTest {

    @Test
    void shouldUse3dWhenSupported() {
        assertTrue(PreviewModeSelector.use3d(true, false));
    }

    @Test
    void shouldFallbackTo2dWhen3dIsUnavailable() {
        assertFalse(PreviewModeSelector.use3d(false, false));
    }

    @Test
    void shouldRespectForced2dMode() {
        assertFalse(PreviewModeSelector.use3d(true, true));
    }

    @Test
    void shouldRemainIn2dWhenUnsupportedAndForced() {
        assertFalse(PreviewModeSelector.use3d(false, true));
    }
}
