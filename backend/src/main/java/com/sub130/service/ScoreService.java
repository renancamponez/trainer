package com.sub130.service;

import com.sub130.domain.DayLog;
import com.sub130.domain.Settings;
import com.sub130.dto.ScoreResult;
import com.sub130.plan.PlannedSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores how well a logged session matched intent, 0-10. Three axes:
 *   - Completion : did the planned distance get done?
 *   - HR discipline : did average HR land where the session wanted it? (the big one on easy days)
 *   - Pace adherence : on easy/long days, was the pace actually easy?
 * Components that lack data (e.g. no HR) drop out and the rest rescale to 10, so a
 * sparsely logged day still gets a fair score rather than being punished for missing inputs.
 */
@Service
public class ScoreService {

    private final PlanService planService;
    private final SettingsService settingsService;
    private final HrZoneService hrZoneService;

    public ScoreService(PlanService planService, SettingsService settingsService, HrZoneService hrZoneService) {
        this.planService = planService;
        this.settingsService = settingsService;
        this.hrZoneService = hrZoneService;
    }

    public ScoreResult score(DayLog log) {
        String date = log.date;
        PlannedSession p = planService.forDate(date);
        if (p == null) return new ScoreResult(date, null, "-", "No planned session on this date.", List.of());
        if ("Rest".equals(p.type)) {
            if (log.done && log.actualKm != null && log.actualKm > 0)
                return new ScoreResult(date, null, "-",
                        "Rest day - you ran anyway. Not scored, but rest is training too.", List.of());
            return new ScoreResult(date, null, "-", "Rest day - nothing to score.", List.of());
        }
        if (!log.done && log.actualKm == null)
            return new ScoreResult(date, null, "-", "Not logged yet.", List.of());

        boolean easy = "Easy".equals(p.type) || "Long".equals(p.type);
        List<ScoreResult.Component> comps = new ArrayList<>();

        // ---- Completion (max 3) ----
        if (p.plannedKm != null && log.actualKm != null) {
            double ratio = log.actualKm / p.plannedKm;
            double pts; String note;
            if (ratio >= 0.95 && ratio <= 1.15) { pts = 3; note = "Distance on target."; }
            else if (ratio > 1.15) { pts = 2.5; note = "Ran long - fine occasionally, but the plan's volume is deliberate."; }
            else if (ratio >= 0.85) { pts = 2.5; note = "A touch short."; }
            else if (ratio >= 0.70) { pts = 1.8; note = "Well short of planned distance."; }
            else { pts = 1.0; note = "Cut short."; }
            comps.add(new ScoreResult.Component("Completion", pts, 3,
                    String.format("%.1f of %.1f km. %s", log.actualKm, p.plannedKm, note)));
        }

        // ---- HR discipline (max 4) - only when a zone applies and HR was recorded ----
        if (p.hrZone != null && log.avgHr != null) {
            long[] band = hrZoneService.bpmBounds(p.hrZone, settingsService.get().lthr);
            long lo = band[0], hi = band[1];
            double pts; String note;
            if (easy) {
                if (log.avgHr <= hi && log.avgHr >= lo) { pts = 4; note = "Avg HR right in " + p.hrZone + " (" + lo + "-" + hi + "). Textbook easy running."; }
                else if (log.avgHr < lo) { pts = 3.6; note = "Even easier than target - never a problem."; }
                else if (log.avgHr <= hi + 5) { pts = 3.0; note = "Slightly above " + p.hrZone + " - ease back a hair."; }
                else if (log.avgHr <= hi + 10) { pts = 2.0; note = "Easy day run too hard (HR " + log.avgHr + " vs " + hi + " ceiling)."; }
                else { pts = 1.0; note = "Way too hard for an easy day - this is the #1 mistake to avoid."; }
            } else { // workout / race: you WANT to reach the zone
                if (log.avgHr >= lo) { pts = 4; note = "Hit the intensity (HR " + log.avgHr + " in/above " + p.hrZone + ")."; }
                else if (log.avgHr >= lo - 5) { pts = 3.2; note = "Just under target intensity."; }
                else if (log.avgHr >= lo - 12) { pts = 2.3; note = "Under-cooked - didn't reach " + p.hrZone + " (avg HR is an underestimate on intervals, though)."; }
                else { pts = 1.5; note = "Well below target - though avg HR reads low on short reps."; }
            }
            comps.add(new ScoreResult.Component("HR discipline", pts, 4, note));
        }

        // ---- Pace adherence (max 2) - easy/long only, where avg pace ~ running pace.
        // On workouts, avg pace is diluted by warmup/cooldown/recoveries, so it isn't a fair read.
        if (easy && p.paceRange != null && log.actualKm != null && log.actualMinutes != null && log.actualKm > 0) {
            long[] bounds = parseRange(p.paceRange);
            if (bounds != null) {
                double actualSec = log.actualMinutes * 60.0 / log.actualKm;
                long fast = bounds[0], slow = bounds[1]; // fast = smaller sec/km
                double pts; String note;
                String actual = paceStr(actualSec);
                if (actualSec >= fast && actualSec <= slow) { pts = 2; note = "Actual " + actual + "/km sits in the easy band. Good."; }
                else if (actualSec > slow) { pts = 1.8; note = "Actual " + actual + "/km - even easier than target. Never a problem."; }
                else if (fast - actualSec <= 10) { pts = 1.6; note = "Actual " + actual + "/km - a touch quick for easy. Fine while HR stays in zone, but watch it."; }
                else if (fast - actualSec <= 25) { pts = 1.2; note = "Actual " + actual + "/km - easy day run too fast. This is how plateaus happen."; }
                else { pts = 0.7; note = "Actual " + actual + "/km - far too fast for an easy day."; }
                comps.add(new ScoreResult.Component("Pace adherence", pts, 2, note));
            }
        }

        if (comps.isEmpty())
            return new ScoreResult(date, null, "-",
                    "Logged as done, but add average HR to get a score.", List.of());

        double got = comps.stream().mapToDouble(ScoreResult.Component::points).sum();
        double max = comps.stream().mapToDouble(ScoreResult.Component::max).sum();
        double score = Math.round((10.0 * got / max) * 10) / 10.0;

        return new ScoreResult(date, score, grade(score), headline(score, easy), comps);
    }

    /** "6:10-6:40" -> {370, 400} (fast bound, slow bound). Single "4:48" -> {288, 288}. */
    private static long[] parseRange(String range) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d):(\\d{2})").matcher(range);
        java.util.List<Long> secs = new ArrayList<>();
        while (m.find()) secs.add(Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2)));
        if (secs.isEmpty()) return null;
        long lo = secs.stream().min(Long::compare).get();
        long hi = secs.stream().max(Long::compare).get();
        return new long[]{lo, hi};
    }

    private static String paceStr(double sec) {
        int m = (int) (sec / 60), s = (int) Math.round(sec % 60);
        return m + ":" + (s < 10 ? "0" + s : "" + s);
    }

    private static String grade(double s) {
        if (s >= 9.5) return "A+";
        if (s >= 9.0) return "A";
        if (s >= 8.0) return "A-";
        if (s >= 7.0) return "B";
        if (s >= 6.0) return "C";
        if (s >= 4.0) return "D";
        return "F";
    }

    private static String headline(double s, boolean easy) {
        if (s >= 9.0) return easy ? "Excellent easy-day discipline." : "Nailed the session.";
        if (s >= 7.5) return "Solid - small things to tidy.";
        if (s >= 6.0) return "Okay, but the details slipped.";
        return easy ? "Easy day run too hard - the classic error." : "Session missed its target.";
    }
}
