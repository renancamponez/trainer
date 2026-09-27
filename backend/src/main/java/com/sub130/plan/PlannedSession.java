package com.sub130.plan;

/**
 * One day of the generated plan. Pure data, no persistence - the plan is
 * deterministic and regenerated on demand; only the athlete's logs are stored.
 * Public fields so Jackson serialises them directly.
 */
public class PlannedSession {
    public int week;          // 0..TOTAL_WEEKS (0 = intro block)
    public String date;       // ISO yyyy-MM-dd
    public String dayName;    // Mon..Sun
    public int phase;         // 1..4
    public String phaseName;
    public String type;       // Rest | Easy | Long | Workout | RACE
    public String session;    // resolved description (with mph annotations)
    public String rawSession; // resolved but un-annotated (no mph, no hill suffix) - for step parsing
    public String warmup;
    public String cooldown;
    public Double plannedKm;  // null on rest days
    public Integer estMinutes;
    public String paceRange;  // min/km
    public String mphRange;
    public String hrZone;     // Z1..Z5, or null where HR is not a useful guide
    public String targetHrBpm; // resolved from LTHR at serve time (not part of the pure plan)
    public String incline;
    public boolean checkpoint;

    // Set only when the morning's readiness changed the day (see DailySessionService).
    public String adjustment;       // TRIMMED | EASY | REST
    public String adjustmentNote;   // why, in plain words
    public String originalSession;  // the session as planned
    public Double originalKm;

    public PlannedSession() {}

    public PlannedSession copy() {
        PlannedSession c = new PlannedSession();
        c.week = week; c.date = date; c.dayName = dayName; c.phase = phase; c.phaseName = phaseName;
        c.type = type; c.session = session; c.rawSession = rawSession; c.warmup = warmup; c.cooldown = cooldown;
        c.plannedKm = plannedKm; c.estMinutes = estMinutes; c.paceRange = paceRange; c.mphRange = mphRange;
        c.hrZone = hrZone; c.targetHrBpm = targetHrBpm; c.incline = incline; c.checkpoint = checkpoint;
        c.adjustment = adjustment; c.adjustmentNote = adjustmentNote;
        c.originalSession = originalSession; c.originalKm = originalKm;
        return c;
    }
}
