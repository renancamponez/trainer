package com.sub130.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sub130.plan.PlanConstants.*;

/**
 * Deterministic port of build_plan.py's build_week / build_week0. Produces the full
 * 367-day plan (3-day intro block + 52 weeks). No Spring dependency so it can be
 * compiled and diffed against the Python generator in isolation.
 */
public final class PlanGenerator {

    private static final Pattern PACE_TOKEN = Pattern.compile("\\d:\\d{2}");
    private static final Pattern PER_KM = Pattern.compile("(\\d:\\d{2})/km");
    private static final Pattern ZONE = Pattern.compile("\\{(\\w+?)([+-]\\d+)?\\}");
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
        Day(String d, String t) { dayName = d; type = t; }
    }

    private static double[] wuCd(double km) { return new double[]{ km <= 8 ? 2 : 3, 2 }; }

    private static String hrZoneOf(Day d) {
        if (d.type.equals("Rest")) return null;
        if (d.raw.toLowerCase().contains("hill sprint")) return null;
        if (d.type.equals("RACE")) return d.raceDist <= 5 ? "Z5" : "Z4";
        if (d.type.equals("Easy") || d.type.equals("Long")) return "Z2";
        String up = d.raw.toUpperCase();
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

    /** Build one training week (1..52) as Mon..Sun. */
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

        Day tue = new Day("Tue", "Workout");
        tue.desc = resolve(W1.get(week), p); tue.km = aKm;
        tue.pace = "warmup/cooldown @ " + easyStr;
        double[] awc = wuCd(aKm); tue.wuKm = awc[0]; tue.cdKm = awc[1];
        days.put("Tue", tue);

        long mon, wed, fri;
        if (cp == null) {
            Day thu = new Day("Thu", "Workout");
            thu.desc = resolve(W2.get(week), p); thu.km = bKm;
            thu.pace = "warmup/cooldown @ " + easyStr;
            double[] bwc = wuCd(bKm); thu.wuKm = bwc[0]; thu.cdKm = bwc[1];
            days.put("Thu", thu);

            Day sat = new Day("Sat", "Long");
            sat.desc = resolve(SPECIAL_LONG.getOrDefault(week, "Long run - conversational the whole way"), p);
            sat.km = longKm; sat.pace = easyStr;
            days.put("Sat", sat);

            long pool = pyRound(vol - (longKm + aKm + bKm));
            if (monRest) { mon = 0; wed = pyRound(pool * 0.60); fri = pool - wed; }
            else { mon = pyRound(pool * 0.38); wed = pyRound(pool * 0.37); fri = pool - mon - wed; }
        } else {
            double wu = cp.distKm > 15 ? 1 : 2;
            double raceKm = cp.distKm + 2 * wu;
            String rp = secToPace((double) cp.targetSec / cp.distKm);
            Day race = new Day(cp.day, "RACE");
            race.desc = cp.label + " - target " + cp.targetLabel + ". " + cp.note;
            race.km = raceKm; race.pace = rp + "/km race pace";
            race.wuKm = wu; race.cdKm = wu; race.raceSec = cp.targetSec; race.raceDist = cp.distKm;
            days.put(cp.day, race);

            Day thu = new Day("Thu", "Workout");
            thu.desc = resolve(W2.get(week), p); thu.km = bKm;
            thu.pace = "warmup/cooldown @ " + easyStr; thu.wuKm = 2; thu.cdKm = 2;
            days.put("Thu", thu);

            double fixed = raceKm + aKm + bKm + 5.0;
            long pool = pyRound(Math.max(vol - fixed, 8));
            if (cp.day.equals("Sat")) {
                mon = pyRound(pool * 0.55); wed = pool - mon; fri = 5;
            } else { // Sunday race: Friday rest, Saturday shakeout
                Day frid = new Day("Fri", "Rest");
                frid.desc = "REST DAY - two days out from the race. Stay off your feet."; frid.pace = "-";
                days.put("Fri", frid);
                Day sat = new Day("Sat", "Easy");
                sat.desc = "Easy shakeout + 4 x 100m strides - race tomorrow"; sat.km = 5; sat.pace = easyStr;
                days.put("Sat", sat);
                mon = pyRound(pool * 0.55); wed = pool - mon; fri = 0;
            }
        }

        Day monD = new Day("Mon", "Easy");
        monD.desc = "Easy run - strength session afterwards (20-30min)"; monD.km = mon; monD.pace = easyStr;
        days.put("Mon", monD);
        Day wedD = new Day("Wed", "Easy");
        wedD.desc = "Easy run - strength session afterwards (20-30min)"; wedD.km = wed; wedD.pace = easyStr;
        days.put("Wed", wedD);
        if (!days.containsKey("Fri")) {
            Day friD = new Day("Fri", "Easy");
            friD.desc = "Easy run - keep it short and gentle, long run tomorrow"; friD.km = fri; friD.pace = easyStr;
            days.put("Fri", friD);
        }
        if (monRest) {
            Day r = new Day("Mon", "Rest");
            r.desc = "EXTRA REST DAY - you raced yesterday. Walk if you like; do not run."; r.pace = "-";
            days.put("Mon", r);
        }

        List<Day> out = new ArrayList<>();
        for (String dn : DAY_NAMES) out.add(days.get(dn));
        return out;
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
        s.incline = d.type.equals("Rest") ? "-" : (hill ? "6-8%" : "1%");

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

    /** The complete plan: week 0 intro + weeks 1..52, one PlannedSession per real date. */
    public static List<PlannedSession> generateAll() {
        List<PlannedSession> all = new ArrayList<>();
        LocalDate[] w0dates = { WEEK0_START, WEEK0_START.plusDays(1), WEEK0_START.plusDays(2) };
        List<Day> w0 = buildWeek0();
        for (int i = 0; i < w0.size(); i++) all.add(finish(w0.get(i), 0, 1, w0dates[i], false));

        for (int wk = 1; wk <= 52; wk++) {
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
