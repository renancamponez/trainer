package com.sub130.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sub130.plan.PlanConstants.*;

/**
 * Deterministic port of build_plan.py's build_week / build_week0. Produces the full
 * plan (3-day intro block + TOTAL_WEEKS weeks). No Spring dependency so it can be
 * compiled and diffed against the Python generator in isolation.
 */
public final class PlanGenerator {

    private static final Pattern PACE_TOKEN = Pattern.compile("\\d:\\d{2}");
    private static final Pattern PER_KM = Pattern.compile("(\\d:\\d{2})/km");
    private static final Pattern ZONE = Pattern.compile("\\{(\\w+?)([+-]\\d+)?\\}");
    // Structured work parsed to compute the true session distance/duration.
    private static final Pattern INTERVAL = Pattern.compile("(\\d+)\\s*x\\s*(\\d+(?:\\.\\d+)?)\\s*(min|km|m)\\b\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern RECOVERY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(s|min|m)\\s*jog");
    private static final Pattern INCLINE = Pattern.compile("(\\d+)%\\s*incline");
    private static final Pattern SINGLE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*min\\s*@\\s*(\\d:\\d{2})/km");
    private static final double MI_PER_KM = 1.609344;

    // ---- small numeric helpers (match Python semantics) ----
    private static long pyRound(double x) { return (long) Math.rint(x); }   // round-half-even

    private static String secToPace(double sec) {
        int m = (int) Math.floor(sec / 60);
        int s = (int) Math.rint(sec % 60);
        return m + ":" + (s < 10 ? "0" + s : "" + s);
    }
    private static int paceToSec(String pace) {
        String[] p = pace.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
    private static double paceToMph(String pace) {
        return 3600.0 / (paceToSec(pace) * MI_PER_KM);
    }
    private static String one(double v) { return String.format(java.util.Locale.US, "%.1f", v); }

    private static String gfmt(double v) { // Python {:g}
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    static String fmtDur(double minutes) {
        if (minutes <= 0) return "-";
        long total = (long) Math.rint(minutes);   // Python round() is round-half-even
        long h = total / 60, m = total % 60;
        return h > 0 ? h + "h " + (m < 10 ? "0" + m : "" + m) + "m" : m + " min";
    }

    private static String mphOf(String field) {
        List<Double> vals = new ArrayList<>();
        Matcher mt = PACE_TOKEN.matcher(field == null ? "" : field);
        while (mt.find()) vals.add(paceToMph(mt.group()));
        if (vals.isEmpty()) return "-";
        vals.sort(Double::compareTo);
        double lo = vals.get(0), hi = vals.get(vals.size() - 1);
        if (vals.size() == 1 || Math.abs(lo - hi) < 0.05) return one(lo);
        return one(lo) + "-" + one(hi);
    }

    private static Integer workSec(String raw) {
        Matcher mt = PACE_TOKEN.matcher(raw == null ? "" : raw);
        Integer min = null;
        while (mt.find()) {
            int s = paceToSec(mt.group());
            if (min == null || s < min) min = s;
        }
        return min;
    }

    private static String annotate(String text) {
        Matcher mt = PER_KM.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (mt.find())
            mt.appendReplacement(sb, Matcher.quoteReplacement(
                mt.group(1) + "/km = " + one(paceToMph(mt.group(1))) + " mph"));
        mt.appendTail(sb);
        return sb.toString();
    }

    private static String resolve(String text, int phase) {
        Matcher mt = ZONE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (mt.find()) {
            String zone = mt.group(1);
            int off = mt.group(2) == null ? 0 : Integer.parseInt(mt.group(2));
            String rep;
            if (zone.equals("easy")) {
                int[] e = easy(phase);
                rep = secToPace(e[0] + off) + "-" + secToPace(e[1] + off);
            } else {
                rep = secToPace(PACE_SEC.get(zone)[phase] + off);
            }
            mt.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        mt.appendTail(sb);
        return sb.toString();
    }

    private static double easyMid(int p) { int[] e = easy(p); return (e[0] + e[1]) / 2.0; }

    // ---- mutable working record during construction ----
    private static final class Day {
        String dayName, type, desc, pace, raw;
        double km;
        double wuKm, cdKm, raceSec, raceDist;
        double mainMin;   // exact minutes for the structured main set (0 = fall back to the blend estimate)
        Day(String d, String t) { dayName = d; type = t; }
    }

    private static double[] wuCd(double km) { return new double[]{ km <= 8 ? 2 : 3, 2 }; }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }

    /**
     * True distance (km) and duration (min) of a structured main set — reps plus the jog
     * recoveries between them — parsed from the resolved session text. Returns null for
     * sessions with no structured reps (easy + strides, hill sprints), whose distance stays
     * the volume-based allocation. This is what makes e.g. "3 x 3km" count as ~9.6 km of work
     * rather than a flat 15%-of-week guess.
     */
    private static double[] structuredMain(String desc, int phase) {
        double em = easyMid(phase);
        double km = 0, sec = 0;
        Matcher iv = INTERVAL.matcher(desc);
        if (iv.find()) {
            int n = Integer.parseInt(iv.group(1));
            double amt = Double.parseDouble(iv.group(2));
            String unit = iv.group(3);
            int pace = paceToSec(iv.group(4));
            double repDist, repDur;
            if (unit.equals("min")) { repDur = amt * 60; repDist = repDur / pace; }
            else if (unit.equals("km")) { repDist = amt; repDur = amt * pace; }
            else { repDist = amt / 1000.0; repDur = repDist * pace; }   // metres
            km += n * repDist; sec += n * repDur;

            Matcher rec = RECOVERY.matcher(desc);
            if (n > 1 && rec.find()) {
                double rv = Double.parseDouble(rec.group(1));
                double recDist, recDur;
                switch (rec.group(2)) {
                    case "min" -> { recDur = rv * 60; recDist = recDur / em; }
                    case "m"   -> { recDist = rv / 1000.0; recDur = recDist * em; }
                    default     -> { recDur = rv; recDist = recDur / em; }   // seconds
                }
                km += (n - 1) * recDist; sec += (n - 1) * recDur;
            }
        } else {
            Matcher sg = SINGLE.matcher(desc);
            if (!sg.find()) return null;                 // no structured set
            double amt = Double.parseDouble(sg.group(1));
            int pace = paceToSec(sg.group(2));
            sec += amt * 60; km += amt * 60 / pace;
        }
        return new double[]{km, sec / 60.0};
    }

    /** If the day carries a structured main set, replace its allocated km/min with the real ones. */
    private static void applyStructured(Day d, int phase) {
        double[] m = structuredMain(d.desc, phase);
        if (m == null) return;
        d.mainMin = m[1];
        d.km = round1(d.wuKm + d.cdKm + m[0]);
    }

    private static String hrZoneOf(Day d) {
        if (d.type.equals("Rest")) return null;
        if (d.raw.toLowerCase().contains("hill sprint")) return null;
        if (d.type.equals("RACE")) return d.raceDist <= 5 ? "Z5" : "Z4";
        if (d.type.equals("Easy") || d.type.equals("Long")) return "Z2";
        String up = d.raw.toUpperCase();
        if (up.contains("HILL") || up.contains("DOWNHILL")) return "Z4";   // incline / downhill reps: effort, not pace
        if (up.contains("VO2") || up.contains("FARTLEK")) return "Z5";
        if (up.contains("STEADY")) return "Z3";
        if (up.contains("THRESHOLD") || up.contains("CRUISE")
                || up.contains("GOAL PACE") || up.contains("RACE PACE")) return "Z4";
        return "Z2";
    }

    private static double sessionMinutes(Day d, int p) {
        double em = easyMid(p);
        if (d.type.equals("Rest") || d.km == 0) return 0;
        if (d.type.equals("RACE")) return d.raceSec / 60 + (d.wuKm + d.cdKm) * em / 60;
        if (d.type.equals("Easy") || d.type.equals("Long")) return d.km * em / 60;
        // Structured workout: warm-up/cool-down at easy + the exact main-set minutes.
        if (d.mainMin > 0) return (d.wuKm + d.cdKm) * em / 60 + d.mainMin;
        double wu = d.wuKm, cd = d.cdKm, main = Math.max(d.km - wu - cd, 0);
        Integer ws = workSec(d.raw);
        String up = d.raw.toUpperCase();
        double blend;
        if (ws == null) blend = d.raw.toLowerCase().contains("hill sprint") ? em - 10 : em;
        else if (up.contains("VO2") || up.contains("FARTLEK")) blend = (2 * ws + em) / 3;
        else if (up.contains("THRESHOLD") || up.contains("STEADY") || up.contains("CRUISE")
                || up.contains("GOAL PACE") || up.contains("RACE PACE")) blend = (6 * ws + em) / 7;
        else blend = (2 * ws + em) / 3;
        return (wu + cd) * em / 60 + main * blend / 60;
    }

    /** Build one training week (1..TOTAL_WEEKS) as Mon..Sun. */
    private static List<Day> buildWeek(int week) {
        int p = phaseOf(week);
        String easyStr = resolve("{easy}", p);
        double vol = VOLUME[week - 1];
        double longKm = LONG[week - 1];
        Checkpoint cp = CHECKPOINTS.get(week);
        boolean monRest = postRaceMondayRest(week);

        long aKm = pyRound(vol * 0.15);
        long bKm = pyRound(vol * 0.14);

        java.util.Map<String, Day> days = new java.util.HashMap<>();

        Day sun = new Day("Sun", "Rest");
        sun.desc = "REST DAY - no running. Mobility/stretching optional."; sun.pace = "-";
        days.put("Sun", sun);

        // Two quality sessions: W1 (primary) and W2 (secondary), placed on Monday and Wednesday so
        // Tuesday and Thursday stay easy (the athlete does a Core 45 class those days). Friday stays
        // easy to protect Saturday's long run.
        Day w1 = workoutDay(W1.get(week), p, aKm, easyStr);
        Day w2 = workoutDay(W2.get(week), p, bKm, easyStr);

        long tue, thu, fri;
        if (cp == null) {
            Day sat = new Day("Sat", "Long");
            sat.desc = resolve(SPECIAL_LONG.getOrDefault(week, week >= 12 ? ROLLING_LONG : "Long run - conversational the whole way"), p);
            sat.km = longKm; sat.pace = easyStr;
            days.put("Sat", sat);

            if (monRest) {
                // Raced Sunday: Monday rests, a single quality session (W1) mid-week on Wednesday.
                Day monR = new Day("Mon", "Rest");
                monR.desc = "EXTRA REST DAY - you raced yesterday. Walk if you like; do not run."; monR.pace = "-";
                days.put("Mon", monR);
                w1.dayName = "Wed"; days.put("Wed", w1);
                long pool = Math.max(pyRound(vol - (longKm + w1.km)), 9);
                tue = pyRound(pool * 0.38); thu = pyRound(pool * 0.34); fri = pool - tue - thu;
            } else {
                w1.dayName = "Mon"; days.put("Mon", w1);
                w2.dayName = "Wed"; days.put("Wed", w2);
                // Easy days (Tue/Thu/Fri) absorb the remainder so the week still totals VOLUME.
                long pool = Math.max(pyRound(vol - (longKm + w1.km + w2.km)), 9);
                tue = pyRound(pool * 0.37); thu = pyRound(pool * 0.37); fri = pool - tue - thu;
            }
        } else {
            double wu = cp.distKm > 15 ? 1 : 2;
            double raceKm = cp.distKm + 2 * wu;
            String rp = secToPace((double) cp.targetSec / cp.distKm);
            Day race = new Day(cp.day, "RACE");
            race.desc = cp.label + " - target " + cp.targetLabel + ". " + cp.note;
            race.km = raceKm; race.pace = rp + "/km race pace";
            race.wuKm = wu; race.cdKm = wu; race.raceSec = cp.targetSec; race.raceDist = cp.distKm;
            days.put(cp.day, race);

            w1.dayName = "Mon"; days.put("Mon", w1);
            w2.dayName = "Wed"; days.put("Wed", w2);

            double fixed = raceKm + w1.km + w2.km + 5.0;
            long pool = pyRound(Math.max(vol - fixed, 8));
            if (cp.day.equals("Sat")) {
                tue = pyRound(pool * 0.5); thu = pool - tue; fri = 4;   // Friday short - race tomorrow
            } else { // Sunday race: Friday rest, Saturday shakeout
                Day frid = new Day("Fri", "Rest");
                frid.desc = "REST DAY - two days out from the race. Stay off your feet."; frid.pace = "-";
                days.put("Fri", frid);
                Day sat = new Day("Sat", "Easy");
                sat.desc = "Easy shakeout + 4 x 100m strides - race tomorrow"; sat.km = 5; sat.pace = easyStr;
                days.put("Sat", sat);
                tue = pyRound(pool * 0.5); thu = pool - tue; fri = 0;
            }
        }

        Day tueD = new Day("Tue", "Easy");
        tueD.desc = "Easy run - easy aerobic day (Core 45 / cross-training)"; tueD.km = tue; tueD.pace = easyStr;
        days.put("Tue", tueD);
        Day thuD = new Day("Thu", "Easy");
        thuD.desc = "Easy run - easy aerobic day (Core 45 / cross-training)"; thuD.km = thu; thuD.pace = easyStr;
        days.put("Thu", thuD);
        if (!days.containsKey("Fri")) {
            Day friD = new Day("Fri", "Easy");
            friD.desc = "Easy run - keep it short and gentle, long run tomorrow"; friD.km = fri; friD.pace = easyStr;
            days.put("Fri", friD);
        }

        List<Day> out = new ArrayList<>();
        for (String dn : DAY_NAMES) out.add(days.get(dn));
        return out;
    }

    /** A quality session from its template: resolves paces, sizes warm-up/cool-down and the true main set. */
    private static Day workoutDay(String template, int p, long km, String easyStr) {
        Day d = new Day("", "Workout");
        d.desc = resolve(template, p); d.km = km;
        d.pace = "warmup/cooldown @ " + easyStr;
        double[] wc = wuCd(km); d.wuKm = wc[0]; d.cdKm = wc[1];
        applyStructured(d, p);
        return d;
    }

    private static List<Day> buildWeek0() {
        int p = 1; String e = resolve("{easy}", p);
        List<Day> out = new ArrayList<>();
        Day fri = new Day("Fri", "Easy");
        fri.desc = "Easy run + 4 x 100m strides - first run of the plan, keep it genuinely easy";
        fri.km = 6; fri.pace = e; out.add(fri);
        Day sat = new Day("Sat", "Long");
        sat.desc = "Long run - conversational the whole way. This sets the tone: easy means easy";
        sat.km = 12; sat.pace = e; out.add(sat);
        Day sun = new Day("Sun", "Rest");
        sun.desc = "REST DAY - no running. Week 1 starts tomorrow."; sun.pace = "-"; out.add(sun);
        return out;
    }

    /** Finalise a working Day into an immutable PlannedSession for a given date. */
    private static PlannedSession finish(Day d, int week, int p, LocalDate date, boolean isCp) {
        d.raw = d.desc;
        boolean hill = d.raw.toLowerCase().contains("hill sprint");
        if (hill) d.desc += " | Outdoors: walk-down recovery. Treadmill: set the incline once and "
                + "leave it, straddle the belt to recover between reps";
        String desc = annotate(d.desc);

        PlannedSession s = new PlannedSession();
        s.week = week; s.date = date.toString(); s.dayName = d.dayName;
        s.phase = p; s.phaseName = PHASE_NAMES[p];
        s.type = d.type; s.session = desc; s.rawSession = d.raw; s.checkpoint = isCp;
        s.plannedKm = d.km == 0 ? null : d.km;
        s.paceRange = d.pace;
        Matcher inc = INCLINE.matcher(d.raw);
        s.incline = d.type.equals("Rest") ? "-"
                : hill ? "6-8%"
                : d.raw.toUpperCase().contains("DOWNHILL") ? "outdoor downhill"
                : inc.find() ? inc.group(1) + "% reps / 1% jog" : "1%";

        double em = easyMid(p);
        String eMph = mphOf(d.pace);
        if (hill) s.mphRange = eMph + " jog / ~8.0-9.0 sprint";
        else if (d.type.equals("Workout")) {
            String w = mphOf(d.raw);
            s.mphRange = !w.equals("-") ? w + " work / " + eMph + " jog" : eMph;
        } else s.mphRange = eMph;

        if (d.type.equals("Rest")) { s.warmup = "-"; s.cooldown = "-"; }
        else if (d.type.equals("RACE")) {
            String strides = d.wuKm >= 2 ? "4 x 100m strides" : "2 x 100m strides";
            s.warmup = gfmt(d.wuKm) + "km easy + " + strides + " (~" + fmtDur(d.wuKm * em / 60 + 4) + ")";
            s.cooldown = gfmt(d.wuKm) + "km walk/jog (~" + fmtDur(d.wuKm * em / 60) + ")";
        } else if (d.type.equals("Workout")) {
            s.warmup = gfmt(d.wuKm) + "km easy + 4 x 100m strides (~" + fmtDur(d.wuKm * em / 60 + 3) + ")";
            s.cooldown = gfmt(d.cdKm) + "km easy (~" + fmtDur(d.cdKm * em / 60) + ")";
        } else if (d.type.equals("Long")) {
            s.warmup = "First 2km at the slow end of easy, then settle";
            s.cooldown = "Last 1km relaxed + stretch";
        } else { s.warmup = "None needed - the whole run is easy"; s.cooldown = "-"; }

        s.estMinutes = (int) Math.rint(sessionMinutes(d, p));
        s.hrZone = hrZoneOf(d);
        return s;
    }

    /** The complete plan: week 0 intro + weeks 1..TOTAL_WEEKS, one PlannedSession per real date. */
    public static List<PlannedSession> generateAll() {
        List<PlannedSession> all = new ArrayList<>();
        LocalDate[] w0dates = { WEEK0_START, WEEK0_START.plusDays(1), WEEK0_START.plusDays(2) };
        List<Day> w0 = buildWeek0();
        for (int i = 0; i < w0.size(); i++) all.add(finish(w0.get(i), 0, 1, w0dates[i], false));

        for (int wk = 1; wk <= TOTAL_WEEKS; wk++) {
            int p = phaseOf(wk);
            boolean isCp = CHECKPOINTS.containsKey(wk);
            List<Day> days = buildWeek(wk);
            for (int i = 0; i < 7; i++) {
                LocalDate date = WEEK1_START.plusDays((long) (wk - 1) * 7 + i);
                boolean dayIsCp = isCp && days.get(i).type.equals("RACE");
                all.add(finish(days.get(i), wk, p, date, dayIsCp));
            }
        }
        return all;
    }

    /** CLI: dump the plan as TSV for diffing against the Python generator. */
    public static void main(String[] args) {
        System.out.println("date\tweek\tday\ttype\tphase\tkm\tmin\tpace\tmph\thr\tincline\tsession");
        for (PlannedSession s : generateAll()) {
            System.out.println(String.join("\t",
                s.date, "" + s.week, s.dayName, s.type, "" + s.phase,
                s.plannedKm == null ? "" : gfmt(s.plannedKm),
                "" + s.estMinutes, s.paceRange, s.mphRange,
                s.hrZone == null ? "" : s.hrZone, s.incline, s.session));
        }
    }
}
