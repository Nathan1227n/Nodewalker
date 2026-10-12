
package io.github.nathan1227n.nodewalker.app;

/**
 * Decide qual modo de visualização deve ser usado na inicialização.
 * Não depende de JavaFX e pode ser testado isoladamente.
 */
public final class PreviewModeSelector {

    private PreviewModeSelector() {
    }

    public static boolean use3d(boolean supports3d, boolean force2d) {
        return supports3d && !force2d;
    }
}
