package io.github.nathan1227n.nodewalker.spike;

import java.util.ArrayList;
import java.util.List;

/** Callback intervals, NOT a GPU presentation counter. Pure clock-fed collector. */
public final class FrameSamples {
    private final long warmup, duration;
    private long first, previous;
    private boolean started, complete;
    private final List<Long> intervals = new ArrayList<>();
    public FrameSamples(long warmup, long duration) {
        if(warmup<0 || duration<=0) throw new IllegalArgumentException("Invalid sampling window");
        this.warmup=warmup; this.duration=duration;
    }
    public boolean accept(long now) {
        if (complete) return true;
        if (started && now<previous) throw new IllegalArgumentException("Non-monotonic clock");
        if (!started) {
            first=now; previous=now; started=true;
            return false;
        }
        long elapsed=now-first, previousElapsed=previous-first;
        // Include the interval that crosses the deadline: do not hide a final stall.
        if(previousElapsed>=warmup && previousElapsed-warmup<duration) intervals.add(now-previous);
        previous=now;
        complete=elapsed>=warmup && elapsed-warmup>=duration;
        return complete;
    }
    public List<Long> intervals() { return List.copyOf(intervals); }
    public boolean complete() { return complete; }
    public long percentile(double q) {
        if(q<0 || q>1) throw new IllegalArgumentException("Quantile 0..1 required");
        if(intervals.isEmpty()) return 0;
        List<Long> sorted = intervals.stream().sorted().toList();
        return sorted.get(Math.max(0,(int)Math.ceil(q*sorted.size())-1)); // nearest-rank
    }
    public long above(long threshold) { return intervals.stream().filter(n -> n>threshold).count(); }
    public double callbacksPerSecond() {
        double sum=intervals.stream().mapToDouble(Long::doubleValue).sum();
        return sum==0?0:intervals.size()*1e9/sum;
    }
}
