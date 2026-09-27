package com.sub130.service;

import com.sub130.dto.ReadinessResult;
import com.sub130.plan.PlanConstants;
import com.sub130.plan.PlanGenerator;
import com.sub130.plan.PlannedSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The session you should actually do on a day: the planned session adjusted by that morning's
 * readiness (HRV, resting HR, sleep score - synced from Garmin). HRV-guided training in short:
 * keep hard days hard when recovered, back off when not, and don't make missed quality up.
 *
 *   GREEN / no data  as planned
 *   AMBER            workout trimmed (~1/3 fewer reps, or shorter reps if only 2-3, +5 s/km);
 *                    long run 80% all easy; easy run unchanged (low end of Z2)
 *   RED              workout -> easy run ~60% of the distance (skip it, don't move it);
 *                    long run 60% easy; easy run 60%, or rest if readiness < 30
 *   RACE / Rest      never changed (a race day only gets a caution note)
 *
 * Scoring, the goal gauge, the step-by-step and the pace chart all read the adjusted session,
 * so following a readiness adjustment counts as following the plan.
 */
@Service
public class DailySessionService {

    private static final Pattern INTERVAL = Pattern.compile("(\\d+)\\s*x\\s*(\\d+(?:\\.\\d+)?)\\s*(min|km|m)\\b\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern SINGLE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*min\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern HILL_SPRINTS = Pattern.compile("(\\d+)(\\s*x\\s*\\d+\\s*s\\s*hill sprints)");
    private static final Pattern LEADING_KM = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*km");

    private final PlanService planService;
    private final ReadinessService readinessService;
    private final HrZoneService hrZoneService;
    private final SettingsService settingsService;

    public DailySessionService(PlanService planService, ReadinessService readinessService,
                               HrZoneService hrZoneService, SettingsService settingsService) {
        this.planService = planService;
        this.readinessService = readinessService;
        this.hrZoneService = hrZoneService;
        this.settingsService = settingsService;
    }

    /** The adjusted session for one date (null outside the plan). */
    public PlannedSession effective(String date) {
        PlannedSession p = planService.forDate(date);
        return p == null ? null : adjust(p, readinessService.compute(date));
    }

    /** Adjusted sessions for a date range, keyed by date - one readiness query for the whole range. */
    public Map<String, PlannedSession> effectiveRange(String start, String end) {
        Map<String, ReadinessResult> ready = new HashMap<>();
        for (ReadinessResult r : readinessService.computeRange(start, end)) ready.put(r.date(), r);
        Map<String, PlannedSession> out = new HashMap<>();
        for (PlannedSession p : planService.all()) {
            if (p.date.compareTo(start) < 0 || p.date.compareTo(end) > 0) continue;
            out.put(p.date, adjust(p, ready.get(p.date)));
        }
        return out;
    }

    /** Apply a readiness result to a planned session. Returns the original when nothing changes. */
    public PlannedSession adjust(PlannedSession p, ReadinessResult r) {
        if (p == null || r == null || r.readinessScore() == null) return p;
        String verdict = r.verdict();
        int score = r.readinessScore();
        String why = why(r);

        if ("RACE".equals(p.type)) {
            if ("GREEN".equals(verdict)) return p;
            PlannedSession c = p.copy();
            c.adjustmentNote = "Race day with readiness " + score + why + ": race it, but start 5-10 s/km "
                    + "conservative and build only if you feel good.";
            return c;
        }
        if ("Rest".equals(p.type) || p.plannedKm == null || "GREEN".equals(verdict)) return p;

        boolean red = "RED".equals(verdict);
        PlannedSession c = p.copy();
        c.originalSession = p.session;
        c.originalKm = p.plannedKm;
        int phase = p.phase == 0 ? 1 : p.phase;

        switch (p.type) {
            case "Workout" -> {
                String trimmed = red ? null : trim(p.rawSession);
                if (trimmed == null) {
                    if (!red) return p;                       // easy + strides: already easy
                    boolean quality = trim(p.rawSession) != null;
                    easySwap(c, Math.max(4, Math.round(p.plannedKm * 0.6)), phase, quality
                            ? "Easy run - readiness low, today's quality session is off. Don't make it up"
                            : "Easy run - readiness low, skip the strides today");
                    c.adjustment = "EASY";
                    c.adjustmentNote = "Readiness " + score + why + ": " + (quality
                            ? "swapped the workout for an easy " + fmtKm(c.plannedKm) + " km. Skip it - one "
                              + "missed session costs nothing, training through a red day does."
                            : "easy " + fmtKm(c.plannedKm) + " km, no strides.")
                            + " Gym: upper body / desk routine only.";
                } else {
                    c.rawSession = trimmed;
                    c.session = PlanGenerator.annotateSession(trimmed);
                    double[] est = PlanGenerator.estimateWorkout(trimmed, leadingKm(p.warmup, 2),
                            leadingKm(p.cooldown, 2), phase);
                    c.plannedKm = est != null ? est[0] : round1(p.plannedKm * 0.85);
                    c.estMinutes = (int) Math.round(est != null ? est[1] : p.estMinutes * 0.85);
                    c.adjustment = "TRIMMED";
                    c.adjustmentNote = "Readiness " + score + why + ": same session, about a third less "
                            + "and 5 s/km easier. Stop early if HR is unusually high for the pace.";
                }
            }
            case "Long" -> {
                double km = Math.max(red ? 6 : 8, Math.round(p.plannedKm * (red ? 0.6 : 0.8)));
                easySwap(c, km, phase, "Long run - readiness " + (red ? "low" : "amber") + ": "
                        + (red ? "60%" : "80%") + " of planned, all easy (skip any pace segment)");
                c.type = "Long";
                c.adjustment = red ? "EASY" : "TRIMMED";
                c.adjustmentNote = "Readiness " + score + why + ": long run cut to " + fmtKm(km)
                        + " km, all easy. Time on feet still counts; the pace work waits for a green day.";
            }
            default -> {   // Easy
                if (!red) {
                    c.adjustmentNote = "Readiness " + score + why + ": run it, but keep HR at the bottom "
                            + "of Z2 and cut it short if you feel flat.";
                    c.originalSession = null; c.originalKm = null;
                    return c;
                }
                if (score < 30) {
                    c.type = "Rest";
                    c.session = c.rawSession = "Rest - readiness very low. A walk is fine; no running today";
                    c.plannedKm = null; c.estMinutes = 0; c.hrZone = null;
                    c.paceRange = "-"; c.mphRange = "-"; c.incline = "-";
                    c.adjustment = "REST";
                    c.adjustmentNote = "Readiness " + score + why + ": rest day. Recovery is the training today.";
                } else {
                    double km = Math.max(3, Math.round(p.plannedKm * 0.6));
                    easySwap(c, km, phase, "Easy run - readiness low: short and very easy");
                    c.adjustment = "EASY";
                    c.adjustmentNote = "Readiness " + score + why + ": shortened to " + fmtKm(km)
                            + " km, very easy. Gym: upper body / desk routine only.";
                }
            }
        }
        // Target-HR band follows the (possibly changed) zone.
        c.targetHrBpm = c.hrZone == null ? null
                : hrZoneService.bpmRange(c.hrZone, settingsService.get().lthr);
        return c;
    }

    /** ~1/3 less work and 5 s/km easier; null when the session has no structured set to trim. */
    static String trim(String raw) {
        if (raw == null) return null;
        Matcher iv = INTERVAL.matcher(raw);
        if (iv.find()) {
            int n = Integer.parseInt(iv.group(1));
            double amt = Double.parseDouble(iv.group(2));
            String unit = iv.group(3);
            String set = n >= 4
                    ? Math.max(3, Math.round(n * 2 / 3.0)) + " x " + num(amt) + unit
                    : n + " x " + num(shorten(amt, unit)) + unit;
            return raw.substring(0, iv.start()) + set + " @ " + ease(iv.group(4)) + "/km" + raw.substring(iv.end());
        }
        Matcher sg = SINGLE.matcher(raw);
        if (sg.find()) {
            long mins = Math.max(10, Math.round(Double.parseDouble(sg.group(1)) * 0.67));
            return raw.substring(0, sg.start()) + mins + "min @ " + ease(sg.group(2)) + "/km" + raw.substring(sg.end());
        }
        Matcher hs = HILL_SPRINTS.matcher(raw);
        if (hs.find()) {
            long n = Math.max(4, Math.round(Integer.parseInt(hs.group(1)) * 2 / 3.0));
            return raw.substring(0, hs.start()) + n + hs.group(2) + raw.substring(hs.end());
        }
        return null;
    }

    private static void easySwap(PlannedSession c, double km, int phase, String text) {
        int[] e = PlanConstants.easy(phase);
        double easyMid = (e[0] + e[1]) / 2.0;
        c.type = "Long".equals(c.type) ? "Long" : "Easy";
        c.paceRange = pace(e[0]) + "-" + pace(e[1]);
        c.mphRange = mph(e[1]) + "-" + mph(e[0]);
        c.rawSession = text;
        c.session = text;
        c.plannedKm = km;
        c.estMinutes = (int) Math.round(km * easyMid / 60);
        c.hrZone = "Z2";
        c.incline = "1%";
        c.warmup = "None needed - the whole run is easy";
        c.cooldown = "-";
    }

    private static String pace(int sec) { return sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60; }
    private static String mph(int secPerKm) {
        return String.format(java.util.Locale.US, "%.1f", 3600.0 / (secPerKm * 1.609344));
    }

    private static String why(ReadinessResult r) {
        List<String> parts = new java.util.ArrayList<>();
        if (r.hrvPctOfBase() != null && r.hrvPctOfBase() < 95) parts.add("HRV " + r.hrvPctOfBase() + "% of usual");
        if (r.restingHr() != null && r.rhrBaseline() != null && r.restingHr() - r.rhrBaseline() >= 3)
            parts.add("resting HR +" + Math.round(r.restingHr() - r.rhrBaseline()));
        if (r.sleepScore() != null && r.sleepScore() < 65) parts.add("sleep " + r.sleepScore());
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    private static double shorten(double amt, String unit) {
        return switch (unit) {
            case "min" -> Math.max(1, Math.round(amt * 0.67));
            case "km" -> Math.max(0.5, Math.round(amt * 0.67 * 2) / 2.0);
            default -> Math.max(200, Math.round(amt * 0.67 / 100) * 100);   // metres
        };
    }

    private static String ease(String pace) {
        String[] a = pace.split(":");
        int s = Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]) + 5;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private static double leadingKm(String s, double dflt) {
        if (s == null) return dflt;
        Matcher m = LEADING_KM.matcher(s);
        return m.find() ? Double.parseDouble(m.group(1)) : dflt;
    }

    private static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }
    private static String fmtKm(double v) { return num(round1(v)); }
    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
