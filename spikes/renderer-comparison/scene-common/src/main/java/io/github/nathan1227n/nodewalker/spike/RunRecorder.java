package io.github.nathan1227n.nodewalker.spike;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Writes only on completion/close. Callback timings explicitly do not certify displayed FPS. */
public final class RunRecorder {
    private final RunOptions options;
    private final FrameSamples samples;
    private final Properties metadata = new Properties();
    private final List<String> events = new ArrayList<>();
    private long first, lastHeap, peakHeap;
    private boolean started, heapSampled, written;
    private long windowCallbacks, focusedCallbacks, visibleCallbacks, iconifiedCallbacks;
    public RunRecorder(String engine, RunOptions options) {
        this.options=options;
        samples=new FrameSamples(options.warmupSeconds()*1_000_000_000L,options.durationSeconds()*1_000_000_000L);
        put("engine",engine); put("fixture",SceneSnapshot.ID); put("terrainPhaseSeed",SceneGeometry.SEED);
        put("n",5); put("cycle",SceneSnapshot.cycle()); put("cycleCost",SceneSnapshot.cycleCost());
        put("status","VISUAL_REFERENCE_NO_SOLVER"); put("logicalCoordinates",SceneSnapshot.cities());
        put("costMatrix",java.util.Arrays.deepToString(SceneSnapshot.matrix()));
        put("captureOnly",options.captureOnly()); put("simple",options.simple()); put("benchmark",options.benchmark());
        put("warmupSeconds",options.warmupSeconds()); put("durationSeconds",options.durationSeconds());
        put("width",1280); put("height",720); put("msaa",0); put("requestedVsync",true); put("fovVertical",45);
        put("java",System.getProperty("java.version")); put("os",System.getProperty("os.name"));
        put("osVersion",System.getProperty("os.version")); put("arch",System.getProperty("os.arch"));
        put("pid",ProcessHandle.current().pid());
        put("metric","application_callback_intervals_not_GPU_presentations");
        try { Files.createDirectories(options.output()); }
        catch(IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    public void put(String key,Object value) { metadata.setProperty(key,String.valueOf(value)); }
    public Path output() { return options.output(); }
    public void event(String text) { events.add(text); }
    public void window(boolean focused, boolean visible, boolean iconified) {
        windowCallbacks++;
        if(focused) focusedCallbacks++;
        if(visible) visibleCallbacks++;
        if(iconified) iconifiedCallbacks++;
    }
    public double seconds(long now) { return started?(now-first)/1e9:0; }
    public boolean frame(long now) {
        if(!started) { first=now; started=true; put("jvmStartToFirstCallbackMillis",ManagementFactory.getRuntimeMXBean().getUptime()); }
        if(!heapSampled || now-lastHeap>=1_000_000_000L) {
            peakHeap=Math.max(peakHeap,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()); lastHeap=now; heapSampled=true;
        }
        return samples.accept(now);
    }
    public void finish() {
        if(written) return;
        written=true;
        put("samplingComplete",samples.complete()); put("sampleCount",samples.intervals().size());
        put("windowCallbacks",windowCallbacks); put("focusedCallbacks",focusedCallbacks);
        put("visibleCallbacks",visibleCallbacks); put("iconifiedCallbacks",iconifiedCallbacks);
        put("windowObservationMethod","callback samples including warmup; not wall-time proportions; visible does not prove unoccluded");
        put("unmodifiedBenchmark",options.benchmark() && events.isEmpty());
        put("callbackHz",String.format(Locale.ROOT,"%.3f",samples.callbacksPerSecond()));
        put("medianIntervalMs",samples.percentile(.5)/1e6); put("p95IntervalMs",samples.percentile(.95)/1e6);
        put("maxIntervalMs",samples.percentile(1)/1e6); put("intervalsOver50ms",samples.above(50_000_000));
        put("intervalsOver100ms",samples.above(100_000_000)); put("sampledPeakHeapBytes",peakHeap);
        put("heapSampling","once_per_second_on_callback_including_warmup; native/VRAM excluded");
        try {
            try(var writer=Files.newBufferedWriter(output().resolve("summary.properties"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                metadata.store(writer,"NodeWalker renderer spike — callback metrics, not GPU FPS");
            }
            List<String> csv=new ArrayList<>(); csv.add("sample,interval_nanos");
            List<Long> values=samples.intervals();
            for(int i=0;i<values.size();i++) csv.add(i+","+values.get(i));
            Files.write(output().resolve("intervals.csv"),csv,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.write(output().resolve("events.txt"),events,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            System.out.println("Metrics: " + output().toAbsolutePath() + " | callback Hz="+metadata.getProperty("callbackHz")
                    +" | samplingComplete="+samples.complete());
        } catch(IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
