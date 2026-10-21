import java.lang.instrument.Instrumentation;

/** Test-only driver: no bytecode changes; only the real default application's JVM is driven. */
public final class CloneSmokeAgent {
    public static void premain(String output, Instrumentation ignored) {
        if (!System.getProperty("sun.java.command", "").startsWith("io.github.nathan1227n.nodewalker.app.NodeWalkerApp")) return;
        Thread driver = new Thread(() -> {
            try { CloneSmokeDriver.start(output); }
            catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(91); }
        }, "clone-smoke-bootstrap");
        driver.setDaemon(true); driver.start();
    }
}
