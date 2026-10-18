package io.github.nathan1227n.nodewalker.spike.fx;

/** Plain launcher permits a distribution with an explicit runtime classpath. */
public final class FxLauncher {
    private FxLauncher() { }
    public static void main(String[] args) { javafx.application.Application.launch(FxPrototype.class,args); }
}
