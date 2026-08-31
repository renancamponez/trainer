package com.sub130.plan;

import java.util.Map;

/**
 * The plan's data, ported verbatim from the spreadsheet generator (build_plan.py).
 * If the athlete's baseline changes, PACE_SEC is the only table that needs editing.
 */
public final class PlanConstants {
    private PlanConstants() {}

    // Plan anchors
    public static final java.time.LocalDate WEEK0_START = java.time.LocalDate.of(2026, 7, 17); // Fri
    public static final java.time.LocalDate WEEK1_START = java.time.LocalDate.of(2026, 7, 20); // Mon

    public static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public static final String[] PHASE_NAMES = {"", "Base", "Strength", "Threshold", "Race-specific"};

    // Training paces in SECONDS PER KM per phase. easy is {fast, slow}. Index by phase 1..4.
    //
    // Recalibrated to the Aug-2026 5K time trial: 24:00 (4:48/km) => VDOT ~40. The earlier tables
    // assumed a fitter athlete, so phase-1 workout paces were too quick. Paces now follow a VDOT
    // progression across the four phases toward the sub-1:30 goal: P1=40 (now), P2=43, P3=46,
    // P4=49-50. Daniels E/M/T/I paces map to easy/steady/thr/vo2; hmp is the fixed 4:16 goal-half
    // pace; brace is the (revised) Sept-27 race pace of ~5:05/km for a realistic 1:47.
    public static int[] easy(int phase) { return EASY[phase]; }
    private static final int[][] EASY = {
        null, {360, 396}, {342, 378}, {326, 360}, {310, 344}
    };
    // steady (marathon pace), thr (threshold), vo2 (interval), hmp (goal half), brace (Sept-27 race)
    // in seconds/km by phase.
    public static final Map<String, int[]> PACE_SEC = Map.of(
        "steady", new int[]{0, 328, 309, 291, 277},
        "thr",    new int[]{0, 306, 288, 272, 259},
        "vo2",    new int[]{0, 282, 266, 250, 238},
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

    // Weekly volume (km) and long-run (km), index 0 = week 1.
    // W2-W4 lightened for travel; 5K TT lands on W6; W9-W10 eased into a ~10-day taper
    // for the Sept 27 half so it can be raced properly.
    public static final double[] VOLUME = {
        40, 38, 40, 36, 44, 46, 52, 42, 54, 42, 37, 56, 60,
        58, 62, 65, 52, 66, 69, 52, 70, 73, 75, 58, 72, 56,
        72, 76, 78, 62, 76, 79, 80, 64, 78, 80, 80, 66, 61,
        64, 78, 80, 64, 78, 80, 58, 78, 80, 74, 64, 52, 52
    };
    public static final double[] LONG = {
        14, 14, 15, 12, 16, 17, 18, 14, 19, 21.1, 12, 18, 20,
        20, 21, 22, 18, 22, 23, 16, 24, 25, 26, 20, 24, 18,
        24, 25, 26, 21, 25, 26, 27, 22, 26, 27, 28, 22, 21,
        25, 26, 27, 22, 26, 28, 18, 26, 27, 24, 20, 16, 21
    };

    // Tuesday workout templates (zone placeholders resolved per phase).
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
        Map.entry(11, "RECOVERY WEEK: easy only - nothing hard, you raced 2 days ago. Skip strides if legs are sore"),
        Map.entry(12, "6 x 3min @ {vo2+5}/km, 2min jog - back to base building"),
        Map.entry(13, "8 x 2min @ {vo2+5}/km, 90s jog"),
        Map.entry(14, "THRESHOLD: 4 x 8min @ {thr}/km, 90s jog"),
        Map.entry(15, "THRESHOLD: 5 x 8min @ {thr}/km, 90s jog"),
        Map.entry(16, "THRESHOLD: 2 x 20min @ {thr+4}/km, 3min jog"),
        Map.entry(17, "THRESHOLD: 3 x 8min @ {thr+2}/km, 90s jog - down week"),
        Map.entry(18, "THRESHOLD: 5 x 8min @ {thr-3}/km, 90s jog"),
        Map.entry(19, "THRESHOLD: 2 x 22min @ {thr+2}/km, 3min jog"),
        Map.entry(20, "THRESHOLD: 4 x 6min @ {thr-3}/km, 90s jog - race week"),
        Map.entry(21, "THRESHOLD: 6 x 8min @ {thr-3}/km, 90s jog"),
        Map.entry(22, "THRESHOLD: 2 x 25min @ {thr}/km, 3min jog"),
        Map.entry(23, "THRESHOLD: 3 x 12min @ {thr-3}/km, 2min jog"),
        Map.entry(24, "THRESHOLD: 4 x 6min @ {thr-6}/km, 90s jog - down week"),
        Map.entry(25, "THRESHOLD: 3 x 15min @ {thr-3}/km, 2min jog"),
        Map.entry(26, "Easy + 4 x 100m strides - race week"),
        Map.entry(27, "THRESHOLD: 5 x 8min @ {thr+2}/km, 90s jog"),
        Map.entry(28, "THRESHOLD: 2 x 20min @ {thr+4}/km, 3min jog"),
        Map.entry(29, "THRESHOLD: 6 x 8min @ {thr}/km, 90s jog"),
        Map.entry(30, "THRESHOLD: 3 x 10min @ {thr+2}/km, 2min jog - down week"),
        Map.entry(31, "THRESHOLD: 2 x 25min @ {thr+2}/km, 3min jog"),
        Map.entry(32, "THRESHOLD: 4 x 12min @ {thr}/km, 2min jog"),
        Map.entry(33, "THRESHOLD: 3 x 15min @ {thr}/km, 2min jog"),
        Map.entry(34, "THRESHOLD: 2 x 15min @ {thr}/km, 3min jog - down week"),
        Map.entry(35, "THRESHOLD: 2 x 30min @ {thr+2}/km, 4min jog"),
        Map.entry(36, "THRESHOLD: 5 x 10min @ {thr-3}/km, 90s jog"),
        Map.entry(37, "THRESHOLD: 3 x 18min @ {thr}/km, 3min jog"),
        Map.entry(38, "THRESHOLD: 3 x 8min @ {thr-3}/km, 90s jog - down week"),
        Map.entry(39, "Easy + 4 x 100m strides - tune-up race week"),
        Map.entry(40, "THRESHOLD: 5 x 10min @ {thr}/km, 90s jog"),
        Map.entry(41, "THRESHOLD: 3 x 15min @ {thr}/km, 2min jog"),
        Map.entry(42, "THRESHOLD: 2 x 25min @ {thr}/km, 3min jog"),
        Map.entry(43, "THRESHOLD: 3 x 10min @ {thr-2}/km, 2min jog - down week"),
        Map.entry(44, "THRESHOLD: 4 x 15min @ {thr-2}/km, 2min jog"),
        Map.entry(45, "THRESHOLD: 2 x 30min @ {thr}/km, 4min jog"),
        Map.entry(46, "THRESHOLD: 4 x 6min @ {thr-7}/km, 90s jog - race week"),
        Map.entry(47, "THRESHOLD: 3 x 20min @ {thr-2}/km, 3min jog"),
        Map.entry(48, "GOAL PACE: 2 x 6km @ {hmp}/km, 4min jog"),
        Map.entry(49, "THRESHOLD: 4 x 15min @ {thr-4}/km, 2min jog"),
        Map.entry(50, "THRESHOLD: 3 x 10min @ {thr-4}/km, 2min jog - taper"),
        Map.entry(51, "THRESHOLD: 3 x 8min @ {hmp}/km, 90s jog - taper"),
        Map.entry(52, "GOAL PACE: 2 x 2km @ {hmp}/km, 3min jog - feel the pace")
    );

    // Thursday workout templates.
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
        Map.entry(12, "STEADY: 30min @ {steady-5}/km"),
        Map.entry(13, "THRESHOLD: 4 x 8min @ {thr}/km, 2min jog"),
        Map.entry(14, "VO2: 8 x 400m @ {vo2}/km, 200m jog"),
        Map.entry(15, "VO2: 10 x 400m @ {vo2}/km, 200m jog"),
        Map.entry(16, "VO2: 6 x 800m @ {vo2}/km, 2min jog"),
        Map.entry(17, "VO2: 6 x 400m @ {vo2-3}/km, 200m jog - down week"),
        Map.entry(18, "VO2: 8 x 600m @ {vo2-3}/km, 90s jog"),
        Map.entry(19, "VO2: 5 x 1000m @ {vo2}/km, 2min jog"),
        Map.entry(20, "Easy + 6 x 100m strides - race in 2 days"),
        Map.entry(21, "VO2: 10 x 600m @ {vo2-5}/km, 90s jog"),
        Map.entry(22, "VO2: 6 x 1000m @ {vo2-3}/km, 2min jog"),
        Map.entry(23, "VO2: 8 x 800m @ {vo2-5}/km, 2min jog"),
        Map.entry(24, "VO2: 5 x 600m @ {vo2-5}/km, 90s jog - down week"),
        Map.entry(25, "VO2: 6 x 800m @ {vo2-7}/km, 2min jog"),
        Map.entry(26, "Easy + 4 x 100m strides - race in 2 days"),
        Map.entry(27, "VO2: 10 x 400m @ {vo2}/km, 200m jog"),
        Map.entry(28, "VO2: 6 x 1000m @ {vo2+3}/km, 2min jog"),
        Map.entry(29, "VO2: 8 x 800m @ {vo2}/km, 2min jog"),
        Map.entry(30, "VO2: 6 x 600m @ {vo2}/km, 90s jog - down week"),
        Map.entry(31, "VO2: 5 x 1200m @ {vo2+3}/km, 2min jog"),
        Map.entry(32, "VO2: 12 x 400m @ {vo2-5}/km, 200m jog"),
        Map.entry(33, "CRUISE: 4 x 2km @ {vo2+7}/km, 3min jog"),
        Map.entry(34, "VO2: 6 x 800m @ {vo2-3}/km, 2min jog - down week"),
        Map.entry(35, "CRUISE: 3 x 3km @ {hmp+4}/km, 3min jog"),
        Map.entry(36, "VO2: 8 x 1000m @ {vo2+3}/km, 2min jog"),
        Map.entry(37, "CRUISE: 2 x 5km @ {hmp+6}/km, 4min jog"),
        Map.entry(38, "VO2: 6 x 400m @ {vo2-5}/km, 200m jog - down week"),
        Map.entry(39, "Easy + 4 x 100m strides - race in 3 days, leg-loosener only"),
        Map.entry(40, "VO2: 8 x 800m @ {vo2}/km, 2min jog"),
        Map.entry(41, "VO2: 6 x 1200m @ {vo2+2}/km, 2min jog"),
        Map.entry(42, "GOAL PACE: 3 x 4km @ {hmp}/km, 3min jog"),
        Map.entry(43, "VO2: 8 x 600m @ {vo2-2}/km, 90s jog - down week"),
        Map.entry(44, "GOAL PACE: 2 x 6km @ {hmp}/km, 4min jog"),
        Map.entry(45, "VO2: 10 x 800m @ {vo2-2}/km, 2min jog"),
        Map.entry(46, "Easy + 6 x 100m strides - race in 2 days"),
        Map.entry(47, "GOAL PACE: 3 x 5km @ {hmp}/km, 3min jog"),
        Map.entry(48, "VO2: 8 x 600m @ {vo2-2}/km, 90s jog"),
        Map.entry(49, "GOAL PACE: 2 x 8km @ {hmp+2}/km, 4min jog"),
        Map.entry(50, "VO2: 6 x 600m @ {vo2}/km, 90s jog - taper"),
        Map.entry(51, "VO2: 5 x 400m @ {vo2}/km, 200m jog - taper"),
        Map.entry(52, "Easy + 4 x 100m strides - race in 3 days, leg-loosener only")
    );

    public static final Map<Integer, String> SPECIAL_LONG = Map.of(
        29, "Long run - final 5km @ {steady}/km",
        36, "Long run - middle 10km @ {steady-10}/km",
        44, "Long run - 3 x 3km @ {hmp}/km inside the run",
        48, "Long run - final 12km @ {hmp+6}/km"
    );

    /** week -> checkpoint race. */
    public static final Map<Integer, Checkpoint> CHECKPOINTS = Map.ofEntries(
        Map.entry(6, new Checkpoint("Sat", "5K TIME TRIAL", 5.0, "24:00", 24 * 60,
            "Done: 24:00 (4:48/km), VDOT ~40. This is now the plan's baseline - every training pace "
          + "is calibrated from it. A genuine, honest starting point off travel-disrupted legs.")),
        Map.entry(10, new Checkpoint("Sun", "SEPT 27 HALF MARATHON (B-race)", 21.1, "1:47:00", 107 * 60,
            "Realistic target from a 24:00 5K plus the wk7/wk9 long runs: 1:47:00 at ~5:05/km. Stretch "
          + "sub-1:45 on a great day; conservative floor 1:50. Endurance is the limiter (longest runs "
          + "are 12-19km, the race is 21.1), so hold 5:05-5:10 the first half and don't chase - fade in "
          + "the back third costs far more than a slightly conservative start saves.")),
        Map.entry(20, new Checkpoint("Sat", "5K RACE", 5.0, "22:40", 22 * 60 + 40,
            "Sharpness check (VDOT ~43). Tells you the phase-2 speed work is landing.")),
        Map.entry(26, new Checkpoint("Sat", "10K RACE", 10.0, "46:30", 46 * 60 + 30,
            "Gate 2 - the decision point (VDOT ~44). Sub-46:30 keeps the 12-month timeline live. 48:00+ "
          + "means commit to the 18-month version: repeat Phase 2 and push the goal race back. That is a "
          + "likely, sensible outcome from a VDOT-40 start, not a failure.")),
        Map.entry(39, new Checkpoint("Sun", "HALF MARATHON TUNE-UP", 21.1, "1:35:30", 95 * 60 + 30,
            "Gate 3: dress rehearsal (VDOT ~47). Practise fuelling and pacing. 1:38+ means the goal is at risk.")),
        Map.entry(46, new Checkpoint("Sat", "10K RACE", 10.0, "42:00", 42 * 60,
            "Gate 4: sub-42:00 keeps sub-1:30 live (VDOT ~48). 41:00 or better makes it likely.")),
        Map.entry(52, new Checkpoint("Sun", "GOAL HALF MARATHON", 21.1, "sub 1:30:00", 5399,
            "Race day. 4:16/km = 8.7 mph. Go get it."))
    );

    /** Weeks whose Monday follows a Sunday race get an extra rest day. */
    public static boolean postRaceMondayRest(int week) {
        return week == 11 || week == 40; // weeks after Sunday races in wk 10 and wk 39
    }

    public static final class Checkpoint {
        public final String day, label, targetLabel, note;
        public final double distKm;
        public final int targetSec;
        public Checkpoint(String day, String label, double distKm, String targetLabel, int targetSec, String note) {
            this.day = day; this.label = label; this.distKm = distKm;
            this.targetLabel = targetLabel; this.targetSec = targetSec; this.note = note;
        }
    }

    public static int phaseOf(int week) {
        if (week == 0) return 1;
        if (week <= 13) return 1;
        if (week <= 26) return 2;
        if (week <= 39) return 3;
        if (week <= 52) return 4;
        throw new IllegalArgumentException("week " + week);
    }
}
