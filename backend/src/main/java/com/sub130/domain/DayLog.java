package com.sub130.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * What the athlete actually did / measured on a given date. Keyed by ISO date so it
 * pairs 1:1 with a generated PlannedSession. Combines the spreadsheet's "actual"
 * columns (Daily Plan) and the morning readiness inputs (Readiness sheet).
 */
@Document("daylogs")
public class DayLog {

    @Id
    public String date;          // ISO yyyy-MM-dd (also the natural key)

    // --- session actuals ---
    public boolean done;
    public Double actualKm;
    public Integer actualMinutes;
    public Integer avgHr;
    public String notes;

    // --- morning readiness inputs ---
    public Double hrvMs;
    public Integer restingHr;
    public Integer sleepScore;   // Garmin sleep score, 0-100

    /** Where the objective actuals (km/time/HR) came from: "manual" or "strava". */
    public String source;

    /** True when the day's main run was on a treadmill (Strava "trainer"); null if unknown. */
    public Boolean treadmill;

    /**
     * Hard efforts detected from the Strava velocity stream (intervals / strides / hill reps),
     * fastest-segment analysis. Null when never synced from a stream; empty when a stream was
     * analysed but no reps stood out. Lets scoring judge each rep instead of the diluted average.
     */
    public java.util.List<Rep> reps;

    /**
     * Actual speed (m/s) downsampled to one value per 10-second bucket, index i => t = i*10 s.
     * Powers the planned-vs-actual pace chart. Null when never synced from a stream.
     */
    public java.util.List<Double> speedSeries;

    /** One detected hard effort. Pace is seconds per km over the segment. */
    public static class Rep {
        public int durationS;
        public int meters;
        public int paceSec;      // s/km
        public Integer avgHr;
        public Integer maxHr;

        public Rep() {}
        public Rep(int durationS, int meters, int paceSec, Integer avgHr, Integer maxHr) {
            this.durationS = durationS; this.meters = meters; this.paceSec = paceSec;
            this.avgHr = avgHr; this.maxHr = maxHr;
        }
    }

    public DayLog() {}
    public DayLog(String date) { this.date = date; }

    /** Session load for ACWR = distance run. Km-based load is a standard runner metric. */
    public Double load() {
        return actualKm;
    }
}
