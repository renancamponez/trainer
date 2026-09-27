package com.sub130.service;

import com.sub130.domain.DayLog;
import com.sub130.domain.Settings;
import com.sub130.dto.ScoreResult;
import com.sub130.plan.PlanConstants;
import com.sub130.plan.PlannedSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern STRIDES = Pattern.compile("(\\d+)\\s*x\\s*100m strides");
    private static final Pattern INTERVAL_C =
            Pattern.compile("(\\d+)\\s*x\\s*\\d+(?:\\.\\d+)?\\s*(?:min|km|m)\\b\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern HILL_C = Pattern.compile("(\\d+)\\s*x\\s*\\d+\\s*s\\s*hill sprints");

    private final DailySessionService dailySessionService;
    private final SettingsService settingsService;
    private final HrZoneService hrZoneService;

    public ScoreService(DailySessionService dailySessionService, SettingsService settingsService, HrZoneService hrZoneService) {
        this.dailySessionService = dailySessionService;
        this.settingsService = settingsService;
        this.hrZoneService = hrZoneService;
    }

    /** Score against the day's session as adjusted by that morning's readiness. */
    public ScoreResult score(DayLog log) {
        return score(log, dailySessionService.effective(log.date));
    }

    /** Score against an already-resolved session (bulk callers avoid a readiness query per day). */
    public ScoreResult score(DayLog log, PlannedSession p) {
        String date = log.date;
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

        // Structured extras that live outside plannedKm and outside the easy HR zone.
        // Strides ("6 x 100m strides") add ~100 m each and are run at ~95% effort, so on an
        // otherwise-easy day they both extend the distance and lift the whole-run average HR.
        String raw = p.rawSession != null ? p.rawSession : p.session;
        int pStrides = 0, pInterval = 0, pHill = 0;
        Integer targetRepPace = null;
        Matcher sm = STRIDES.matcher(raw == null ? "" : raw);
        if (sm.find()) pStrides = Integer.parseInt(sm.group(1));
        Matcher im = INTERVAL_C.matcher(raw == null ? "" : raw);
        if (im.find()) { pInterval = Integer.parseInt(im.group(1)); targetRepPace = paceToSec(im.group(2)); }
        Matcher hm = HILL_C.matcher(raw == null ? "" : raw);
        if (hm.find()) pHill = Integer.parseInt(hm.group(1));
        double stridesKm = pStrides * 0.1;
        boolean hasStrides = pStrides > 0;
        // Velocity-detectable reps are speed intervals + strides; treadmill hill sprints add incline
        // not speed, so they rarely surface in the stream and aren't counted toward the rep target.
        int repTarget = (pStrides + pInterval) > 0 ? pStrides + pInterval : pHill;
        boolean prescribedReps = repTarget > 0;

        // ---- Completion (max 3) ----
        if (p.plannedKm != null && log.actualKm != null) {
            double plannedKm = p.plannedKm + stridesKm;   // count the strides toward the target
            double ratio = log.actualKm / plannedKm;
            double pts; String note;
            if (ratio >= 0.95 && ratio <= 1.15) { pts = 3; note = "Distance on target."; }
            else if (ratio > 1.15) { pts = 2.5; note = "Ran long - fine occasionally, but the plan's volume is deliberate."; }
            else if (ratio >= 0.85) { pts = 2.5; note = "A touch short."; }
            else if (ratio >= 0.70) { pts = 1.8; note = "Well short of planned distance."; }
            else { pts = 1.0; note = "Cut short."; }
            String tgt = hasStrides ? String.format("%.1f km incl. strides", plannedKm)
                                    : String.format("%.1f km", plannedKm);
            comps.add(new ScoreResult.Component("Completion", pts, 3,
                    String.format("%.1f of %s. %s", log.actualKm, tgt, note)));
        }

        // ---- Reps (max 4) - per-rep read from the Strava velocity stream, for interval/stride days.
        // This judges the hard efforts themselves (count + intensity) instead of a whole-run average
        // that warm-up, cool-down and recoveries dilute. Only when reps were actually detected;
        // otherwise (not synced, or hill/treadmill efforts the stream can't see) we fall back below.
        boolean repScored = false;
        if (prescribedReps && log.reps != null && !log.reps.isEmpty()) {
            repScored = true;
            List<DayLog.Rep> reps = log.reps;
            int done = reps.size();
            int medPace = median(reps.stream().map(r -> r.paceSec).sorted().toList());
            List<Integer> hrList = reps.stream().map(r -> r.avgHr).filter(java.util.Objects::nonNull).sorted().toList();
            Integer medHr = hrList.isEmpty() ? null : median(hrList);

            int lthr = settingsService.get().lthr;
            long easyHi = hrZoneService.bpmBounds("Z2", lthr)[1];
            int phase = p.phase == 0 ? 1 : p.phase;
            int easyFast = PlanConstants.easy(phase)[0];

            double countF = done >= repTarget ? 1.0
                    : (repTarget - done == 1 && repTarget >= 4) ? 0.85
                    : Math.max(0.0, (double) done / repTarget);

            double intenF; String intenNote;
            if (pInterval > 0 && targetRepPace != null) {
                double r = (double) medPace / targetRepPace;   // >1 = slower than prescribed
                if (r <= 1.05)      { intenF = 1.0;  intenNote = "on prescribed pace"; }
                else if (r <= 1.10) { intenF = 0.85; intenNote = "just off pace"; }
                else if (r <= 1.18) { intenF = 0.65; intenNote = "under target pace"; }
                else                { intenF = 0.45; intenNote = "well under target pace"; }
            } else { // strides: reward genuine hard efforts, don't nitpick exact pace
                boolean hardHr = medHr != null && medHr >= easyHi;
                boolean fast = medPace <= easyFast - 30;
                intenF = (hardHr || fast) ? 1.0 : 0.65;
                intenNote = (hardHr || fast) ? "genuinely hard" : "barely faster than easy";
            }

            double pts = Math.round((4.0 * (0.55 * countF + 0.45 * intenF)) * 10) / 10.0;
            String cnt = done >= repTarget ? done + " reps" : done + " of " + repTarget + " reps";
            String note = String.format("%s @ ~%s/km%s - %s.", cnt, paceStr(medPace),
                    medHr != null ? " · HR ~" + medHr : "", intenNote);
            comps.add(new ScoreResult.Component("Reps", pts, 4, note));
        }

        // ---- HR discipline (max 4) - only when a zone applies and HR was recorded ----
        if (!repScored && p.hrZone != null && log.avgHr != null) {
            long[] band = hrZoneService.bpmBounds(p.hrZone, settingsService.get().lthr);
            long lo = band[0], hi = band[1];
            // Strides are meant to be hard; allow the whole-run average to sit a few bpm above the
            // easy ceiling before we treat it as running the easy portion too hard.
            long hiEff = hi + (easy && hasStrides ? 6 : 0);
            double pts; String note;
            if (easy) {
                if (log.avgHr <= hiEff && log.avgHr >= lo) {
                    pts = 4;
                    note = hasStrides && log.avgHr > hi
                            ? "Avg HR " + log.avgHr + " - the strides lifted it above " + hi + ", which is exactly what they're for. Easy portion looks controlled."
                            : "Avg HR right in " + p.hrZone + " (" + lo + "-" + hi + "). Textbook easy running.";
                }
                else if (log.avgHr < lo) { pts = 3.6; note = "Even easier than target - never a problem."; }
                else if (log.avgHr <= hiEff + 5) { pts = 3.0; note = "Slightly above " + p.hrZone + " - ease back a hair."; }
                else if (log.avgHr <= hiEff + 10) { pts = 2.0; note = "Easy day run too hard (HR " + log.avgHr + " vs " + hi + " ceiling)."; }
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

    /** Median of a pre-sorted, non-empty list. */
    private static int median(List<Integer> sorted) { return sorted.get(sorted.size() / 2); }

    /** "4:30" -> 270 seconds. */
    private static int paceToSec(String pace) {
        String[] a = pace.split(":");
        return Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
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
