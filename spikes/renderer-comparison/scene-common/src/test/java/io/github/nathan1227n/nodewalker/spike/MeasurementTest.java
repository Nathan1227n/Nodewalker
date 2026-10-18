package io.github.nathan1227n.nodewalker.spike;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class MeasurementTest {
    @Test void excludesWarmupAndIncludesFinalStallWithoutSleeping() {
        FrameSamples s=new FrameSamples(100,100);
        assertFalse(s.accept(1000));assertFalse(s.accept(1090));assertFalse(s.accept(1100));
        assertFalse(s.accept(1110));assertFalse(s.accept(1130));assertTrue(s.accept(1250));
        assertEquals(List.of(10L,20L,120L),s.intervals());
        assertEquals(20,s.percentile(.5));assertEquals(120,s.percentile(.95));
        assertEquals(1,s.above(100));
        assertTrue(s.accept(1500));assertEquals(3,s.intervals().size());
    }
    @Test void exactBoundaryAndZeroWarmup() {
        FrameSamples s=new FrameSamples(0,20);s.accept(0);s.accept(10);assertTrue(s.accept(20));
        assertEquals(List.of(10L,10L),s.intervals());assertEquals(100_000_000,s.callbacksPerSecond(),1e-5);
    }
    @Test void acceptsNegativeMonotonicClockOrigins() {
        FrameSamples s=new FrameSamples(10,20);
        assertFalse(s.accept(-100));assertFalse(s.accept(-90));
        assertFalse(s.accept(-80));assertTrue(s.accept(-70));
        assertEquals(List.of(10L,10L),s.intervals());
    }
    @Test void rejectsBackwardsClockAndInvalidWindow() {
        FrameSamples s=new FrameSamples(0,20);s.accept(50);
        assertThrows(IllegalArgumentException.class,()->s.accept(49));
        assertThrows(IllegalArgumentException.class,()->new FrameSamples(-1,1));
        assertThrows(IllegalArgumentException.class,()->new FrameSamples(0,0));
    }
    @Test void noSamplesIsNotACompletedMeasurement() {
        FrameSamples s=new FrameSamples(100,100);assertFalse(s.complete());assertEquals(0,s.percentile(.95));assertEquals(0,s.callbacksPerSecond());
    }
    @ParameterizedTest @ValueSource(strings={"--unknown","--duration=0","--duration=301","--warmup=-1","--warmup=121"})
    void rejectsInvalidOptions(String arg) {assertThrows(IllegalArgumentException.class,()->RunOptions.parse(List.of(arg)));}
    @Test void captureAndBenchmarkCannotBeMixed() {
        assertThrows(IllegalArgumentException.class,()->RunOptions.parse(List.of("--capture-only","--benchmark")));
    }
    @Test void recordsFixtureScopeAndInterventions(@TempDir Path dir) throws Exception {
        RunOptions options=RunOptions.parse(List.of("--benchmark","--warmup=0","--duration=1","--output="+dir));
        RunRecorder recorder=new RunRecorder("test",options);
        recorder.window(true,true,false);recorder.window(false,true,true);
        recorder.frame(10);recorder.event("screenshot");assertTrue(recorder.frame(1_000_000_010L));recorder.finish();recorder.finish();
        Properties result=new Properties();try(var r=Files.newBufferedReader(dir.resolve("summary.properties"))) {result.load(r);}
        assertEquals("true",result.getProperty("samplingComplete"));assertEquals("false",result.getProperty("unmodifiedBenchmark"));
        assertEquals("application_callback_intervals_not_GPU_presentations",result.getProperty("metric"));
        assertEquals("VISUAL_REFERENCE_NO_SOLVER",result.getProperty("status"));assertEquals("2338",result.getProperty("cycleCost"));
        assertEquals("2",result.getProperty("windowCallbacks"));assertEquals("1",result.getProperty("focusedCallbacks"));
        assertEquals("2",result.getProperty("visibleCallbacks"));assertEquals("1",result.getProperty("iconifiedCallbacks"));
        assertEquals(2,Files.readAllLines(dir.resolve("intervals.csv")).size());
    }
}
