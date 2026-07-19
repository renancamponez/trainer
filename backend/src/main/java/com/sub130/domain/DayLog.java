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

    public DayLog() {}
    public DayLog(String date) { this.date = date; }

    /** Session load for ACWR = distance run. Km-based load is a standard runner metric. */
    public Double load() {
        return actualKm;
    }
}
