package com.sub130.plan;

import java.util.Map;

/**
 * The plan's data. Weeks 1-10 are history (the build to the Sept-27 Boulderthon) and stay as they
 * were run; weeks 11-41 are the build to the goal race, the Colorado Half Marathon on Sun 2 May 2027
 * (Poudre Canyon to Fort Collins: net ~100 m downhill, ~60 m of climbing, start ~1,620 m).
 */
public final class PlanConstants {
    private PlanConstants() {}

    // Plan anchors
    public static final java.time.LocalDate WEEK0_START = java.time.LocalDate.of(2026, 7, 17); // Fri
    public static final java.time.LocalDate WEEK1_START = java.time.LocalDate.of(2026, 7, 20); // Mon
    public static final int TOTAL_WEEKS = 41;
    public static final java.time.LocalDate GOAL_RACE = java.time.LocalDate.of(2027, 5, 2);   // Sun, wk 41

    public static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public static final String[] PHASE_NAMES = {"", "Base", "Hills & strength", "Threshold", "Race-specific"};

    // Training paces in SECONDS PER KM per phase. easy is {fast, slow}. Index by phase 1..4.
    //
    // Phase 1 (wks 1-11) stays as calibrated from the Aug-2026 treadmill 5K (24:00, VDOT ~40).
    // The Sept-27 race showed outdoor fitness is a little below that, so Phase 2 holds the same
    // paces rather than stepping up: the hill block trains at the fitness actually in hand. Phases
    // 3 and 4 step to VDOT ~43 and ~46 - what consistent training realistically delivers by spring.
    // hmp is the 4:16 goal pace (sub-1:30); it is used in short doses as a reality check, not as a
    // training pace. brace is the old Sept-27 race pace, only referenced by the week 9-10 history.
    public static int[] easy(int phase) { return EASY[phase]; }
    private static final int[][] EASY = {
        null, {360, 396}, {360, 396}, {342, 378}, {326, 360}
    };
    public static final Map<String, int[]> PACE_SEC = Map.of(
        "steady", new int[]{0, 328, 328, 309, 291},
        "thr",    new int[]{0, 306, 306, 288, 272},
        "vo2",    new int[]{0, 282, 282, 266, 250},
        "hmp",    new int[]{0, 256, 256, 256, 256},
        "brace",  new int[]{0, 304, 304, 304, 304}
    );

    // HR zones as a fraction of LTHR (threshold HR), Friel-style.
    public static final String[] ZONE_NAMES = {"Z1", "Z2", "Z3", "Z4", "Z5"};
    public static final double[][] ZONE_PCT = {
        {0.70, 0.81}, {0.81, 0.89}, {0.90, 0.93}, {0.94, 0.99}, {1.00, 1.06}
    };
    public static final String[] ZONE_USE = {
        "Recovery", "Easy / aerobic", "Steady / tempo", "Threshold", "VO2 / interval"
    };
    public static double[] zonePct(String zone) {
        for (int i = 0; i < ZONE_NAMES.length; i++)
            if (ZONE_NAMES[i].equals(zone)) return ZONE_PCT[i];
        return null;
    }

    // Weekly volume (km) and long run (km), index 0 = week 1.
    // Wks 1-10 as run. From wk 11 the ramp starts where the athlete actually is (~30 km/wk, not the
    // 56-60 the old table jumped to), builds ~3 km/wk with a down week every 4th, peaks ~56 km in
    // March-April, then tapers over the last three weeks.
    public static final double[] VOLUME = {
        40, 38, 40, 36, 44, 46, 52, 42, 54, 42,          // 1-10  history
        28,                                               // 11    post-race recovery
        34, 37, 40, 32, 42, 45, 42, 34, 46,              // 12-20 hills & strength
        48, 50, 38, 50, 52, 46, 40, 53, 55, 56,          // 21-30 threshold
        44, 54, 56, 50, 36, 56, 56, 54, 46, 38, 34       // 31-41 race-specific + taper
    };
    public static final double[] LONG = {
        14, 14, 15, 12, 16, 17, 18, 14, 19, 21.1,        // 1-10
        12,                                               // 11
        14, 15, 16, 12, 16, 17, 14, 13, 18,              // 12-20
        18, 19, 14, 19, 20, 15, 15, 20, 21, 22,          // 21-30
        16, 20, 22, 21.1, 14, 22, 20, 20, 16, 13, 21.1   // 31-41
    };

    // Monday (primary) workout templates. Zone placeholders resolve per phase.
    public static final Map<Integer, String> W1 = Map.ofEntries(
        Map.entry(1, "8 x 20s hill sprints + 4 x 100m strides"),
        Map.entry(2, "8 x 20s hill sprints + 4 x 100m strides"),
        Map.entry(3, "10 x 20s hill sprints + 4 x 100m strides"),
        Map.entry(4, "6 x 20s hill sprints - down week"),
        Map.entry(5, "10 x 20s hill sprints + 6 x 100m strides"),
        Map.entry(6, "Fartlek: 8 x 1min @ {vo2}/km, 1min jog"),
        Map.entry(7, "Fartlek: 10 x 1min @ {vo2}/km, 1min jog"),
        Map.entry(8, "Easy + 6 x 100m strides - down week"),
        Map.entry(9, "RACE PACE: 3 x 3km @ {brace}/km (Sept 27 target pace), 2min jog"),
        Map.entry(10, "RACE WEEK: 2 x 2km @ {brace}/km, 3min jog - light sharpener, stay well within yourself"),
        Map.entry(11, "RECOVERY WEEK: easy only - nothing hard, you raced on Sunday. Skip strides if legs are sore"),
        // --- Hills & strength (12-20): treadmill incline builds the climbing you've been missing ---
        Map.entry(12, "HILLS: 8 x 1min @ {steady}/km on 6% incline, 90s jog (1% incline) - drive the arms, quick steps"),
        Map.entry(13, "HILLS: 10 x 1min @ {steady}/km on 6% incline, 90s jog (1% incline)"),
        Map.entry(14, "HILLS: 5 x 3min @ {steady+20}/km on 5% incline, 2min jog (1% incline) - strength-endurance"),
        Map.entry(15, "HILLS: 6 x 1min @ {steady}/km on 6% incline, 90s jog - down week"),
        Map.entry(16, "HILLS: 6 x 3min @ {steady+20}/km on 5% incline, 2min jog (1% incline)"),
        Map.entry(17, "HILL TEMPO: 24min @ {steady+15}/km, alternating 3min at 4% / 3min at 1% incline"),
        Map.entry(18, "HILLS: 8 x 1min @ {steady}/km on 6% incline, 90s jog - keep it light, 5K Saturday"),
        Map.entry(19, "HILLS: 4 x 3min @ {steady+20}/km on 5% incline, 2min jog - down week"),
        Map.entry(20, "HILLS: 6 x 4min @ {steady+20}/km on 5% incline, 2min jog (1% incline)"),
        // --- Threshold (21-30) ---
        Map.entry(21, "THRESHOLD: 4 x 8min @ {thr}/km, 90s jog"),
        Map.entry(22, "THRESHOLD: 3 x 10min @ {thr}/km, 2min jog"),
        Map.entry(23, "THRESHOLD: 3 x 6min @ {thr}/km, 90s jog - down week"),
        Map.entry(24, "THRESHOLD: 2 x 15min @ {thr+4}/km, 3min jog"),
        Map.entry(25, "THRESHOLD: 5 x 6min @ {thr-3}/km, 90s jog"),
        Map.entry(26, "THRESHOLD: 3 x 6min @ {thr}/km, 90s jog - 10K Saturday"),
        Map.entry(27, "THRESHOLD: 3 x 8min @ {thr+3}/km, 90s jog - down week"),
        Map.entry(28, "THRESHOLD: 2 x 20min @ {thr+4}/km, 3min jog"),
        Map.entry(29, "THRESHOLD: 4 x 10min @ {thr}/km, 2min jog"),
        Map.entry(30, "THRESHOLD: 3 x 12min @ {thr-3}/km, 2min jog"),
        // --- Race-specific (31-41): goal pace in short doses + downhill durability ---
        Map.entry(31, "THRESHOLD: 3 x 8min @ {thr}/km, 90s jog - down week"),
        Map.entry(32, "GOAL PACE: 5 x 1km @ {hmp}/km, 2min jog - reality check: how does 4:16 feel?"),
        Map.entry(33, "THRESHOLD: 2 x 20min @ {thr}/km, 3min jog"),
        Map.entry(34, "THRESHOLD: 4 x 5min @ {thr}/km, 90s jog - half marathon Sunday"),
        Map.entry(35, "RECOVERY WEEK: easy only - you raced on Sunday. Strides only if legs feel good"),
        Map.entry(36, "GOAL PACE: 4 x 2km @ {hmp}/km, 2min jog"),
        Map.entry(37, "THRESHOLD: 3 x 12min @ {thr-3}/km, 2min jog"),
        Map.entry(38, "GOAL PACE: 3 x 2km @ {hmp}/km, 2min jog"),
        Map.entry(39, "THRESHOLD: 3 x 10min @ {thr}/km, 2min jog - taper begins"),
        Map.entry(40, "GOAL PACE: 2 x 3km @ {hmp+5}/km, 3min jog - taper"),
        Map.entry(41, "GOAL PACE: 2 x 2km @ {hmp}/km, 3min jog - feel the pace, race Sunday")
    );

    // Wednesday (secondary) workout templates.
    public static final Map<Integer, String> W2 = Map.ofEntries(
        Map.entry(1, "Easy + 6 x 100m strides"),
        Map.entry(2, "Easy + 6 x 100m strides"),
        Map.entry(3, "STEADY: 20min @ {steady}/km"),
        Map.entry(4, "Easy + 4 x 100m strides - down week"),
        Map.entry(5, "STEADY: 2 x 10min @ {steady}/km, 2min jog"),
        Map.entry(6, "Easy + 4 x 100m strides - 5K time trial Saturday, keep this light"),
        Map.entry(7, "STEADY: 3 x 8min @ {steady-10}/km, 2min jog"),
        Map.entry(8, "STEADY: 20min @ {steady}/km - down week"),
        Map.entry(9, "STEADY: 25min @ {steady-5}/km - keep it controlled, race is 10 days out"),
        Map.entry(10, "Easy + 4 x 100m strides - race in 3 days, this is just a leg-loosener"),
        Map.entry(11, "RECOVERY WEEK: easy + 6 x 100m strides"),
        Map.entry(12, "Easy + 6 x 100m strides"),
        Map.entry(13, "STEADY: 20min @ {steady}/km"),
        Map.entry(14, "Fartlek: 8 x 1min @ {vo2}/km, 1min jog"),
        Map.entry(15, "Easy + 6 x 100m strides - down week"),
        Map.entry(16, "STEADY: 2 x 12min @ {steady}/km, 2min jog"),
        Map.entry(17, "VO2: 6 x 800m @ {vo2}/km, 2min jog"),
        Map.entry(18, "Easy + 4 x 100m strides - 5K time trial Saturday, keep this light"),
        Map.entry(19, "Easy + 6 x 100m strides - down week"),
        Map.entry(20, "THRESHOLD: 3 x 8min @ {thr}/km, 90s jog"),
        Map.entry(21, "HILLS: 10 x 1min @ {steady}/km on 6% incline, 90s jog - hill maintenance"),
        Map.entry(22, "VO2: 5 x 1000m @ {vo2}/km, 2min jog"),
        Map.entry(23, "Easy + 6 x 100m strides - down week"),
        Map.entry(24, "HILLS: 6 x 3min @ {steady+20}/km on 5% incline, 2min jog - hill maintenance"),
        Map.entry(25, "STEADY: 30min @ {steady}/km"),
        Map.entry(26, "Easy + 4 x 100m strides - 10K Saturday, keep this light"),
        Map.entry(27, "Easy + 6 x 100m strides - down week"),
        Map.entry(28, "HILLS: 8 x 1.5min @ {steady}/km on 6% incline, 90s jog"),
        Map.entry(29, "VO2: 6 x 1000m @ {vo2}/km, 2min jog"),
        Map.entry(30, "CRUISE: 3 x 3km @ {thr+5}/km, 2min jog"),
        Map.entry(31, "Easy + 6 x 100m strides - down week"),
        Map.entry(32, "DOWNHILL: 6 x 3min @ {thr}/km on a gentle 2-3% downhill road (outdoors), 3min jog back up"),
        Map.entry(33, "GOAL PACE: 4 x 2km @ {hmp+10}/km, 2min jog"),
        Map.entry(34, "Easy + 4 x 100m strides - half marathon Sunday"),
        Map.entry(35, "RECOVERY WEEK: easy + 6 x 100m strides"),
        Map.entry(36, "DOWNHILL: 5 x 4min @ {thr-5}/km on a gentle downhill road (outdoors), 3min jog back up"),
        Map.entry(37, "GOAL PACE: 2 x 5km @ {hmp+10}/km, 4min jog"),
        Map.entry(38, "VO2: 6 x 800m @ {vo2}/km, 2min jog"),
        Map.entry(39, "GOAL PACE: 3 x 2km @ {hmp}/km, 2min jog - taper"),
        Map.entry(40, "VO2: 5 x 400m @ {vo2}/km, 200m jog - taper"),
        Map.entry(41, "Easy + 4 x 100m strides - race Sunday, leg-loosener only")
    );

    /** Default long-run text from the hill block on: outdoors, where the climbs actually are. */
    public static final String ROLLING_LONG = "Long run - outdoors on a rolling route; conversational, run every climb by effort, not pace";

    public static final Map<Integer, String> SPECIAL_LONG = Map.ofEntries(
        Map.entry(17, "Long run - outdoors, hilly route; practise climbing on tired legs in the last 4km"),
        Map.entry(20, "Long run - outdoors, hilly route; keep cadence up on every climb (short, quick steps)"),
        Map.entry(22, "Long run - hilly route, last 4km @ {steady}/km including the climbs"),
        Map.entry(25, "Long run - outdoors, rolling; middle 6km @ {steady}/km"),
        Map.entry(28, "Long run - hilly route, final 5km @ {steady}/km"),
        Map.entry(30, "Long run - outdoors, rolling; 2 x 4km @ {steady-10}/km inside the run"),
        Map.entry(32, "Long run - OUTDOORS on a net-downhill route (Poudre or Boulder canyon), last 5km @ {steady}/km"),
        Map.entry(33, "Long run - 3 x 3km @ {hmp+15}/km inside the run"),
        Map.entry(36, "Long run - OUTDOORS on the Colorado Half course or a net-downhill canyon road, final 6km @ {hmp+15}/km"),
        Map.entry(37, "Long run - rolling route, middle 8km @ {steady-10}/km"),
        Map.entry(38, "Long run - net-downhill route, middle 8km @ {hmp+10}/km"),
        Map.entry(39, "Long run - easy, net-downhill route; last 2km @ {hmp}/km")
    );

    /** week -> checkpoint race. */
    public static final Map<Integer, Checkpoint> CHECKPOINTS = Map.ofEntries(
        Map.entry(6, new Checkpoint("Sat", "5K TIME TRIAL", 5.0, "24:00", 24 * 60,
            "Done: 24:00 (4:48/km) - on the treadmill. Treadmill times run optimistic outdoors, which the "
          + "Sept-27 race confirmed; the next time trial is outdoors.")),
        Map.entry(10, new Checkpoint("Sun", "SEPT 27 BOULDERTHON HALF (B-race)", 21.1, "1:47:00", 107 * 60,
            "Result 1:57:44 on a hilly course (+160 m) at 26 C. Went out at 5:06-5:14 with HR at 160+ from km 3, "
          + "slowed at the same HR from km 7, and cadence collapsed on the km 16-17 and 21 climbs. "
          + "Target was set off a treadmill 5K - too aggressive for outdoor fitness.", 0.975)),
        Map.entry(18, new Checkpoint("Sat", "5K TIME TRIAL (OUTDOORS)", 5.0, "24:00", 24 * 60,
            "Outdoors on a flat route this time - the honest calibration. 24:00 outdoors = VDOT ~40; faster "
          + "means the hill block is paying off. Re-tune paces from this result.")),
        Map.entry(26, new Checkpoint("Sat", "10K RACE", 10.0, "48:00", 48 * 60,
            "Gate (VDOT ~42). Sub-47:00 keeps a May sub-1:30 alive on paper; 49:00+ means May becomes a "
          + "~1:40 race and sub-1:30 moves to an autumn-2027 half.")),
        Map.entry(34, new Checkpoint("Sun", "HALF MARATHON TUNE-UP", 21.1, "1:42:00", 102 * 60,
            "Dress rehearsal (VDOT ~44), ideally flat or downhill. Practise fuelling and pacing. Sub-1:38 "
          + "keeps 1:30 in play for Colorado; 1:42+ means race Colorado at ~4:50/km.")),
        Map.entry(41, new Checkpoint("Sun", "COLORADO HALF MARATHON (GOAL)", 21.1, "sub 1:30:00", 5399,
            "Fort Collins, from Poudre Canyon: net ~100 m downhill, small rises near km 7 and 10, start "
          + "~1,620 m. 4:16/km = 8.7 mph. Protect the quads on the canyon descent - don't bank time in "
          + "the first 8 km.", 1.015))
    );

    /** Weeks whose Monday follows a Sunday race get an extra rest day. */
    public static boolean postRaceMondayRest(int week) {
        return week == 11 || week == 35; // weeks after the Sunday races in wk 10 and wk 34
    }

    public static final class Checkpoint {
        public final String day, label, targetLabel, note;
        public final double distKm;
        public final int targetSec;
        /** Multiplier turning a time on this course into a flat-course equivalent (hilly < 1 < downhill). */
        public final double courseFactor;
        public Checkpoint(String day, String label, double distKm, String targetLabel, int targetSec, String note) {
            this(day, label, distKm, targetLabel, targetSec, note, 1.0);
        }
        public Checkpoint(String day, String label, double distKm, String targetLabel, int targetSec,
                          String note, double courseFactor) {
            this.day = day; this.label = label; this.distKm = distKm;
            this.targetLabel = targetLabel; this.targetSec = targetSec; this.note = note;
            this.courseFactor = courseFactor;
        }
    }

    public static int phaseOf(int week) {
        if (week <= 11) return 1;
        if (week <= 20) return 2;
        if (week <= 30) return 3;
        if (week <= TOTAL_WEEKS) return 4;
        throw new IllegalArgumentException("week " + week);
    }
}
