package io.github.nathan1227n.nodewalker.spike;

import java.nio.file.Path;
import java.util.List;

public record RunOptions(boolean simple, boolean benchmark, boolean captureOnly, int warmupSeconds, int durationSeconds, Path output) {
    public static RunOptions parse(List<String> args) {
        boolean simple=false, benchmark=false, captureOnly=false;
        int warmup=10, duration=60;
        Path output=Path.of("spikes/renderer-comparison/target/runs", "run-" + System.currentTimeMillis());
        for (String arg : args) {
            if (arg.equals("--simple")) simple=true;
            else if (arg.equals("--benchmark")) benchmark=true;
            else if (arg.equals("--capture-only")) captureOnly=true;
            else if (arg.startsWith("--warmup=")) warmup=Integer.parseInt(arg.substring(9));
            else if (arg.startsWith("--duration=")) duration=Integer.parseInt(arg.substring(11));
            else if (arg.startsWith("--output=")) output=Path.of(arg.substring(9));
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        if (warmup<0 || warmup>120 || duration<1 || duration>300) throw new IllegalArgumentException("warmup: 0..120; duration: 1..300 seconds");
        if (benchmark && captureOnly) throw new IllegalArgumentException("Capture separately from benchmarks");
        return new RunOptions(simple,benchmark,captureOnly,warmup,duration,output);
    }
}
