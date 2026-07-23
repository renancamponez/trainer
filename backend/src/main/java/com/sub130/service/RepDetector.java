package com.sub130.service;

import com.sub130.domain.DayLog.Rep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds hard efforts (intervals / strides / hill reps) in a run's velocity stream.
 *
 * A "rep" is a contiguous stretch run clearly faster than the day's easy/recovery pace.
 * We take the median of all moving samples as the easy baseline and flag stretches above
 * a multiple of it. This is device-agnostic: treadmill runs still carry a velocity stream,
 * so it works whether or not the athlete lapped the reps. Validated against a real 6 x 100m
 * strides session (detected 6 of 6).
 */
public final class RepDetector {

    private static final double MOVING_MS = 0.3;   // below this we treat as stopped/standing
    private static final double FAST_MULT = 1.20;  // "hard" = >= 1.20x the median moving speed
    private static final int MIN_REP_S = 8;        // ignore blips shorter than this
    private static final int MIN_GAP_S = 5;        // merge segments split by a brief dropout

    private RepDetector() {}

    /**
     * @param time  seconds elapsed per sample (required, strictly increasing)
     * @param vel   metres/second per sample (required)
     * @param hr    beats/min per sample, or null if the run has no HR
     * @param dist  cumulative metres per sample, or null (then distance is integrated from vel)
     */
    public static List<Rep> detect(int[] time, double[] vel, int[] hr, double[] dist) {
        List<Rep> reps = new ArrayList<>();
        if (time == null || vel == null || time.length != vel.length || time.length < 10) return reps;
        int n = time.length;

        double[] moving = Arrays.stream(vel).filter(v -> v > MOVING_MS).sorted().toArray();
        if (moving.length < 5) return reps;
        double median = moving[moving.length / 2];
        double thr = median * FAST_MULT;

        // Collect [start,end] index ranges above threshold, merging brief dropouts.
        int start = -1, lastFast = -1;
        List<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (vel[i] >= thr) {
                if (start < 0) start = i;
                lastFast = i;
            } else if (start >= 0 && time[i] - time[lastFast] > MIN_GAP_S) {
                ranges.add(new int[]{start, lastFast});
                start = -1;
            }
        }
        if (start >= 0) ranges.add(new int[]{start, lastFast});

        for (int[] r : ranges) {
            int a = r[0], b = r[1];
            int dur = time[b] - time[a];
            if (dur < MIN_REP_S) continue;

            double meters = (dist != null) ? Math.max(0, dist[b] - dist[a]) : integrate(time, vel, a, b);
            if (meters < 20) continue;                 // too short to be a real rep
            int paceSec = (int) Math.round(1000.0 * dur / meters);

            Integer avgHr = null, maxHr = null;
            if (hr != null && hr.length == n) {
                long sum = 0; int cnt = 0, mx = 0;
                for (int i = a; i <= b; i++) if (hr[i] > 0) { sum += hr[i]; cnt++; mx = Math.max(mx, hr[i]); }
                if (cnt > 0) { avgHr = (int) Math.round((double) sum / cnt); maxHr = mx; }
            }
            reps.add(new Rep(dur, (int) Math.round(meters), paceSec, avgHr, maxHr));
        }
        return reps;
    }

    /** Distance from the trapezoid of velocity when no distance stream is available. */
    private static double integrate(int[] time, double[] vel, int a, int b) {
        double m = 0;
        for (int i = a; i < b; i++) m += (vel[i] + vel[i + 1]) / 2.0 * (time[i + 1] - time[i]);
        return m;
    }
}
