package com.sub130.service;

import com.sub130.dto.WorkoutDetail;
import com.sub130.dto.WorkoutDetail.Step;
import com.sub130.plan.PlanConstants;
import com.sub130.plan.PlannedSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands a planned session into followable steps (warm-up, each rep, each recovery, cool-down)
 * with duration, distance, pace, treadmill speed, incline and HR for every piece.
 */
@Service
public class WorkoutService {

    private static final double MI = 1.609344;
    private static final Pattern INTERVAL = Pattern.compile("(\\d+)\\s*x\\s*(\\d+(?:\\.\\d+)?)\\s*(min|km|m)\\b\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern RECOVERY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(s|min|m)\\s*jog");
    private static final Pattern HILL = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)\\s*s\\s*hill sprints");
    private static final Pattern STRIDES = Pattern.compile("(\\d+)\\s*x\\s*100m strides");
    private static final Pattern SINGLE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*min\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern RACEPACE = Pattern.compile("(\\d:\\d{2})/km");
    private static final Pattern INCLINE = Pattern.compile("(\\d+)%\\s*incline");

    private final HrZoneService hr;
    private final SettingsService settings;

    public WorkoutService(HrZoneService hr, SettingsService settings) {
        this.hr = hr;
        this.settings = settings;
    }

    public WorkoutDetail expand(PlannedSession s) {
        if (s == null) return null;
        int lthr = settings.get().lthr;
        int p = s.phase == 0 ? 1 : s.phase;
        int[] e = PlanConstants.easy(p);
        String easyRange = secPace(e[0]) + "-" + secPace(e[1]);
        String easyMph = one(mph(e[1])) + "-" + one(mph(e[0]));
        String raw = s.rawSession != null ? s.rawSession : s.session;

        List<Step> steps = new ArrayList<>();
        switch (s.type) {
            case "Rest" -> { return new WorkoutDetail(s.date, "Rest day", raw, List.of()); }
            case "Easy", "Long" -> easyOrLong(s, raw, easyRange, easyMph, lthr, p, steps);
            case "RACE" -> race(s, easyRange, easyMph, lthr, p, steps);
            default -> workout(s, raw, easyRange, easyMph, lthr, p, steps);
        }
        return new WorkoutDetail(s.date, title(s), s.session, steps);
    }

    // ---------- easy / long ----------
    private void easyOrLong(PlannedSession s, String raw, String easyRange, String easyMph,
                            int lthr, int p, List<Step> steps) {
        boolean isLong = "Long".equals(s.type);
        steps.add(new Step("Run", isLong ? "Long run" : "Easy run",
                (s.plannedKm == null ? "—" : trim(s.plannedKm) + " km"),
                easyRange, easyMph,
                raw.contains("rolling incline") ? "rolling 1% ↔ 3-4%"
                        : raw.contains("decline") ? "-1 to -2% if available"
                        : raw.contains("incline") ? "see session" : "1%",
                "Z2 · " + band("Z2", lthr),
                isLong ? "conversational the whole way; start at the slow end" : "conversational — full sentences, no strain"));
        // embedded quality inside a long run (e.g. "final 5km @ 4:55/km")
        Matcher q = RACEPACE.matcher(raw);
        if (isLong && q.find()) {
            String pace = q.group(1);
            String seg = raw.contains("final") ? "final segment" : raw.contains("middle") ? "middle segment" : "surges within the run";
            steps.add(new Step("Pick-up", seg, "as prescribed", pace + "/km", one(mph(paceSec(pace))), "1%",
                    zoneHr(paceSec(pace), p, lthr), "lift to this pace for the marked part, then ease home"));
        }
        strides(raw, steps);
    }

    // ---------- race ----------
    private void race(PlannedSession s, String easyRange, String easyMph, int lthr, int p, List<Step> steps) {
        double wuKm = s.plannedKm != null && s.plannedKm > 18 ? 1 : 2;
        steps.add(new Step("Warm-up", "Easy jog", trim(wuKm) + " km", easyRange, easyMph, "1%",
                "Z1-2", "loosen up + a few strides; longer races need less warm-up"));
        Matcher m = RACEPACE.matcher(s.paceRange);
        if (m.find()) {
            String pace = m.group(1);
            steps.add(new Step("Race", raceLabel(s), raceDist(s), pace + "/km", one(mph(paceSec(pace))), "1%",
                    zoneHr(paceSec(pace), p, lthr), "even effort, negative-split if you can"));
        }
        steps.add(new Step("Cool-down", "Easy jog", trim(wuKm) + " km", easyRange, easyMph, "1%", "Z1", "ease down, refuel"));
    }

    // ---------- workouts ----------
    private void workout(PlannedSession s, String raw, String easyRange, String easyMph,
                         int lthr, int p, List<Step> steps) {
        Matcher hillM = HILL.matcher(raw);
        Matcher ivM = INTERVAL.matcher(raw);
        Matcher singleM = SINGLE.matcher(raw);
        boolean hasHill = hillM.find();
        boolean hasIv = ivM.find();
        boolean hasSingle = !hasIv && singleM.find();

        // No structured set (e.g. "Easy + 6 x 100m strides"): it's just an easy run with strides.
        if (!hasHill && !hasIv && !hasSingle) {
            steps.add(new Step("Run", "Easy run",
                    (s.plannedKm == null ? "—" : trim(s.plannedKm) + " km"),
                    easyRange, easyMph, "1%", "Z2 · " + band("Z2", lthr),
                    "conversational — full sentences, no strain"));
            strides(raw, steps);
            return;
        }

        steps.add(new Step("Warm-up", "Easy jog", trim(warmKm(s.warmup)) + " km", easyRange, easyMph, "1%",
                "Z1-2", "build gradually; finish with 4 × 100m strides to prime the legs"));

        if (hasHill) {
            int n = Integer.parseInt(hillM.group(1));
            int sec = Integer.parseInt(hillM.group(2));
            steps.add(new Step("Main set", n + " × hill sprint", sec + " s", "—", "8.5-9.5", "6-8%",
                    "—", "near-maximal — let the hill supply the resistance; drive the arms, stay tall"));
            steps.add(new Step("Recovery", "between sprints", "~90 s", "walk", "walk / straddle belt", "leave incline",
                    "—", "FULL recovery — these build power, not fatigue"));
        } else if (hasIv) {
            intervalSet(raw, ivM, easyMph, lthr, p, steps);
        } else {
            String amt = singleM.group(1), pace = singleM.group(2);
            boolean hillTempo = raw.toUpperCase().contains("HILL TEMPO");
            steps.add(new Step("Main set", hillTempo ? "Hill tempo" : "Steady effort", intMin(amt) + " min", pace + "/km",
                    one(mph(paceSec(pace))), hillTempo ? "alternate 4% / 1%" : "1%",
                    hillTempo ? "Z3-4 · " + band("Z4", lthr) : zoneHr(paceSec(pace), p, lthr),
                    hillTempo ? "hold the belt speed; let effort rise on the 4% blocks and settle on the 1% ones"
                              : "controlled and continuous — comfortably hard, not a race"));
        }

        strides(raw, steps); // hill days carry strides in the main line
        steps.add(new Step("Cool-down", "Easy jog", trim(warmCoolKm(s.cooldown)) + " km",
                easyRange, easyMph, "1%", "Z1", "ease down fully, then stretch"));
    }

    private boolean intervalSet(String raw, Matcher iv, String easyMph, int lthr, int p, List<Step> steps) {
        int n = Integer.parseInt(iv.group(1));
        double amt = Double.parseDouble(iv.group(2));
        String unit = iv.group(3);
        String pace = iv.group(4);
        int ps = paceSec(pace);
        String label = effortLabel(raw, ps, p);

        String amount;
        if (unit.equals("min")) {
            double km = amt * 60 / ps;
            amount = trim(amt) + " min  (≈ " + trim(round1(km)) + " km)";
        } else if (unit.equals("km")) {
            amount = trim(amt) + " km  (≈ " + dur(amt * ps) + ")";
        } else { // metres
            double km = amt / 1000;
            amount = (int) amt + " m  (≈ " + dur(km * ps) + ")";
        }
        boolean hill = label.startsWith("hill"), downhill = label.startsWith("downhill");
        Matcher inc = INCLINE.matcher(raw);
        String incline = downhill ? "-2% (decline treadmill)" : inc.find() ? inc.group(1) + "%" : "1%";
        // On an incline the belt speed understates the effort, so the target is HR, not pace.
        String hrTarget = (hill || downhill) ? "Z4 · " + band("Z4", lthr) : zoneHr(ps, p, lthr);
        steps.add(new Step("Main set", n + " × " + label, amount, pace + "/km",
                one(mph(ps)), incline, hrTarget, repCue(label)));

        Matcher rec = RECOVERY.matcher(raw);
        if (rec.find() && n > 1) {
            String rv = rec.group(1), ru = rec.group(2);
            String ramt = ru.equals("s") ? intMin(rv) + " s"
                    : ru.equals("min") ? intMin(rv) + " min"
                    : (int) Double.parseDouble(rv) + " m";
            steps.add(new Step("Recovery", "(" + (n - 1) + " ×) between reps", ramt,
                    "easy jog",
                    easyMph + " or slower", "1%", "—", "keep moving, let HR drop before the next rep"));
        }
        return true;
    }

    // ---------- strides ----------
    private void strides(String raw, List<Step> steps) {
        Matcher st = STRIDES.matcher(raw);
        if (st.find()) {
            int n = Integer.parseInt(st.group(1));
            steps.add(new Step("Strides", n + " × 100m stride", "≈ 100 m (18-22 s)", "~3:50/km", "9.0-9.6", "1%",
                    "—", "smooth and fast (~95%), NOT an all-out sprint; walk/jog ~45 s between"));
        }
    }

    // ---------- helpers ----------
    private String effortLabel(String raw, int paceSec, int phase) {
        String u = raw.toUpperCase();
        if (u.contains("DOWNHILL")) return "downhill rep";
        if (u.contains("HILL")) return "hill rep";
        if (u.contains("THRESHOLD")) return "threshold rep";
        if (u.contains("VO2")) return "VO2 rep (hard)";
        if (u.contains("FARTLEK")) return "surge";
        if (u.contains("CRUISE")) return "cruise interval";
        if (u.contains("GOAL PACE")) return "goal-pace rep";
        if (u.contains("RACE PACE")) return "race-pace rep";
        if (u.contains("STEADY")) return "steady rep";
        // unlabeled: infer from pace
        return switch (zone(paceSec, phase)) {
            case "Z5" -> "VO2 rep (hard)";
            case "Z4" -> "threshold rep";
            case "Z3" -> "steady rep";
            default -> "rep";
        };
    }

    private String repCue(String label) {
        if (label.startsWith("hill")) return "effort, not speed: drive the arms, short quick steps, stay tall — keep cadence up";
        if (label.startsWith("downhill")) return "quick light steps, slight forward lean, let gravity work — don't brake with the quads";
        if (label.startsWith("VO2")) return "hard but controlled — ~3K-5K effort, smooth form";
        if (label.startsWith("threshold")) return "\"comfortably hard\" — could hold ~1h in a race";
        if (label.startsWith("goal-pace")) return "lock into goal race rhythm — this is the target feel";
        if (label.startsWith("surge")) return "quick pickup, then float the recovery";
        return "hold the pace evenly across the rep";
    }

    /** Zone from pace using the phase's pace anchors (so unlabeled hard reps still get a band). */
    private String zone(int paceSec, int phase) {
        int vo2 = PlanConstants.PACE_SEC.get("vo2")[phase];
        int thr = PlanConstants.PACE_SEC.get("thr")[phase];
        int steady = PlanConstants.PACE_SEC.get("steady")[phase];
        if (paceSec <= vo2 + 3) return "Z5";
        if (paceSec <= thr + 3) return "Z4";
        if (paceSec <= steady + 6) return "Z3";
        return "Z2";
    }
    private String zoneHr(int paceSec, int phase, int lthr) {
        String z = zone(paceSec, phase);
        return z + " · " + band(z, lthr);
    }
    private String band(String zone, int lthr) {
        String b = hr.bpmRange(zone, lthr);
        return b == null ? "—" : b.replace(" bpm", "");
    }

    private double warmKm(String warmup) { return firstNum(warmup, 3); }
    private double warmCoolKm(String cd) { return firstNum(cd, 2); }
    private double firstNum(String s, double dflt) {
        if (s == null) return dflt;
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*km").matcher(s);
        return m.find() ? Double.parseDouble(m.group(1)) : dflt;
    }

    private String raceLabel(PlannedSession s) {
        String head = s.session.split(" - ")[0];
        return head.isEmpty() ? "Race" : head;
    }
    private String raceDist(PlannedSession s) {
        if (s.plannedKm == null) return "—";
        double d = s.plannedKm > 18 ? 21.1 : s.plannedKm > 12 ? 10 : 5; // strip warm-up/cool-down km
        return trim(d) + " km";
    }
    private String title(PlannedSession s) {
        return switch (s.type) {
            case "RACE" -> "Race day";
            case "Long" -> "Long run";
            case "Easy" -> "Easy run";
            case "Rest" -> "Rest day";
            default -> "Workout";
        };
    }

    private static int paceSec(String pace) { String[] a = pace.split(":"); return Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]); }
    private static double mph(int paceSec) { return 3600.0 / (paceSec * MI); }
    private static String secPace(int sec) { return sec / 60 + ":" + pad(sec % 60); }
    private static String one(double v) { return String.format(java.util.Locale.US, "%.1f", v); }
    private static String pad(int n) { return n < 10 ? "0" + n : "" + n; }
    private static String dur(double sec) {
        int s = (int) Math.round(sec);
        return s >= 3600 ? s / 3600 + ":" + pad(s % 3600 / 60) + ":" + pad(s % 60) : s / 60 + ":" + pad(s % 60);
    }
    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
    private static String intMin(String s) { return "" + (int) Double.parseDouble(s); }
    private static String trim(double v) { return v == Math.rint(v) ? "" + (long) v : "" + v; }
}
