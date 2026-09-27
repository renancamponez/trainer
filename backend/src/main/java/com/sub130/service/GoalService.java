package com.sub130.service;

import com.sub130.domain.DayLog;
import com.sub130.dto.GoalProjection;
import com.sub130.dto.ReadinessResult;
import com.sub130.plan.PlanConstants;
import com.sub130.plan.PlannedSession;
import com.sub130.repo.DayLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Goal outlook" — how closely you are following the plan to the goal race time. 100% means you are
 * doing everything the plan asks: the prescribed volume, every key session, well executed, and the
 * latest checkpoint on target. It is a plan-adherence gauge, not a validated race-time probability.
 *
 *   outlook = adherence · checkpoint · trajectory · risk
 *
 *   adherence   last 4 weeks: >=95% of prescribed km, all key sessions, sessions averaging >=8.5/10
 *   checkpoint  the LATEST checkpoint vs its target (a new on-target result replaces an old miss)
 *   trajectory  is current fitness on the line the checkpoint targets draw to the goal? If behind,
 *               each s/week of extra improvement needed beyond the plan's line costs 5 points
 *   risk        injury/overreaching discount from readiness (RED days, high ACWR)
 */
@Service
public class GoalService {

    private static final LocalDate PLAN_START = LocalDate.of(2026, 7, 17);
    private static final int PLAN_DAYS = (int) ChronoUnit.DAYS.between(PLAN_START, PlanConstants.GOAL_RACE);
    // Fitness is tracked as a flat-course half equivalent. The goal is a time on the Colorado course,
    // which is net downhill, so the flat-equivalent bar is a little slower than the goal itself.
    private static final double GOAL_SEC = PlanConstants.CHECKPOINTS.get(PlanConstants.TOTAL_WEEKS).targetSec;   // 1:40:00
    private static final double GOAL_FLAT_SEC = GOAL_SEC
            * PlanConstants.CHECKPOINTS.get(PlanConstants.TOTAL_WEEKS).courseFactor;
    private static final double BASE_SEC = 6720;      // ~1:52 flat-equivalent at plan start (1:50:46 on the downhill Colorado course, May 2026)
    private static final int TRAJECTORY_FROM_WEEK = 11;   // checkpoints set for the current (Colorado) plan

    private final PlanService planService;
    private final DayLogRepository logRepo;
    private final ScoreService scoreService;
    private final ReadinessService readinessService;
    private final DailySessionService dailySessionService;

    public GoalService(PlanService planService, DayLogRepository logRepo, ScoreService scoreService,
                       ReadinessService readinessService, DailySessionService dailySessionService) {
        this.planService = planService;
        this.logRepo = logRepo;
        this.scoreService = scoreService;
        this.readinessService = readinessService;
        this.dailySessionService = dailySessionService;
    }

    public GoalProjection project(LocalDate asOf) {
        long elapsedDays = clampL(ChronoUnit.DAYS.between(PLAN_START, asOf), 0, PLAN_DAYS);
        double remainingWeeks = Math.max((PLAN_DAYS - elapsedDays) / 7.0, 0.0);
        String asOfStr = asOf.toString();

        Map<String, DayLog> logs = new HashMap<>();
        for (DayLog l : logRepo.findAllByOrderByDateAsc()) logs.put(l.date, l);
        List<PlannedSession> plan = planService.all();

        // --- adherence over the last 4 weeks (completed days only) ---
        // Recent-only on purpose: the gauge answers "am I doing what the plan asks now?", so old
        // gaps stop counting once four good weeks are in.
        String recentCutoff = asOf.minusDays(28).toString();
        double plannedKm = 0, actualKm = 0;
        int plannedWorkouts = 0, doneWorkouts = 0;
        List<Double> scores = new ArrayList<>();
        // Readiness-adjusted sessions: a trimmed or swapped day done as adjusted is fully on plan.
        Map<String, PlannedSession> adjusted = dailySessionService.effectiveRange(recentCutoff, asOfStr);
        for (PlannedSession planned : plan) {
            if (planned.date.compareTo(asOfStr) > 0 || planned.date.compareTo(recentCutoff) < 0) continue;
            PlannedSession s = adjusted.getOrDefault(planned.date, planned);
            DayLog log = logs.get(s.date);
            boolean done = log != null && log.done && log.actualKm != null;
            if (s.date.equals(asOfStr) && !done) continue;       // today isn't a miss until it's over
            if (s.plannedKm != null) { plannedKm += s.plannedKm; if (done) actualKm += log.actualKm; }
            if ("Workout".equals(s.type)) { plannedWorkouts++; if (done) doneWorkouts++; }
            if (done) { Double sc = scoreService.score(log, s).score(); if (sc != null) scores.add(sc); }
        }
        double volAdh = plannedKm > 0 ? actualKm / plannedKm : 1.0;
        double workAdh = plannedWorkouts > 0 ? (double) doneWorkouts / plannedWorkouts : 1.0;
        double execAvg = scores.isEmpty() ? 8.5 : scores.stream().mapToDouble(x -> x).average().orElse(8.5);
        double volScore = Math.min(1, volAdh / 0.95);
        double workScore = Math.min(1, workAdh);
        double execScore = Math.min(1, execAvg / 8.5);
        double adherence = 0.45 * volScore + 0.35 * workScore + 0.20 * execScore;

        // --- checkpoints: the latest one reached ---
        Double lastRatio = null, lastCpEqHalf = null;
        LocalDate lastCpDate = null;
        String lastCpDetail = null;
        for (PlannedSession s : plan) {
            if (!s.checkpoint || s.date.compareTo(asOfStr) > 0) continue;   // include today if raced
            PlanConstants.Checkpoint cp = PlanConstants.CHECKPOINTS.get(s.week);
            if (cp == null) continue;
            // A solo time trial is often run a few days off the scheduled date, and the run logged
            // on the exact day may just be an easy jog. So take the best (fastest) effort near the
            // checkpoint's distance within a window around it, rather than only the exact-date run.
            String winStart = LocalDate.parse(s.date).minusDays(28).toString();
            DayLog best = null; double bestPace = Double.MAX_VALUE;
            for (DayLog l : logs.values()) {
                if (l == null || !l.done || l.actualKm == null || l.actualMinutes == null || l.actualKm <= 0) continue;
                if (l.date.compareTo(winStart) < 0 || l.date.compareTo(asOfStr) > 0) continue;
                if (l.actualKm < cp.distKm * 0.90 || l.actualKm > cp.distKm * 1.12) continue;   // ~same distance
                double pace = l.actualMinutes * 60.0 / l.actualKm;
                if (pace < bestPace) { bestPace = pace; best = l; }
            }
            if (best == null) continue;
            // A treadmill effort is converted to its race equivalent (the belt runs ~2% fast).
            double tm = Boolean.TRUE.equals(best.treadmill) ? PlanConstants.TREADMILL : 1.0;
            double targetPace = cp.targetSec / cp.distKm;
            lastRatio = targetPace / (bestPace * tm);                        // >1 = faster than target (same course)
            // Fitness uses the flat-course equivalent, so a hilly race isn't read as lost fitness.
            lastCpEqHalf = riegelHalf(best.actualKm, bestPace * best.actualKm * tm * cp.courseFactor);
            lastCpDate = LocalDate.parse(best.date);
            lastCpDetail = cp.label + ": " + fmt(bestPace * best.actualKm)
                    + (tm > 1 ? " treadmill (≈" + fmt(bestPace * best.actualKm * tm) + " race-equiv)" : "")
                    + " vs " + cp.targetLabel + " target ("
                    + (lastRatio >= 1 ? "on/ahead" : "behind") + ")";
        }
        // On or ahead of target = full marks; each 1% behind costs 5 points.
        double cpScore = lastRatio == null ? 1.0 : clamp(1 - (1 - Math.min(1, lastRatio)) * 5, 0, 1);

        // --- current fitness: last measured result, carried forward along the plan's line in
        // proportion to how fully you've done the training since (no checkpoint yet: from baseline) ---
        double expectedNow = expectedOnPlan(asOf);
        double anchor = lastCpEqHalf != null ? lastCpEqHalf : BASE_SEC;
        double anchorExpected = lastCpDate != null ? expectedOnPlan(lastCpDate) : BASE_SEC;
        double currentEqHalf = clamp(anchor - Math.max(0, anchorExpected - expectedNow) * adherence, 5100, 7500);

        // --- trajectory: on the checkpoint line, or can the gap still be closed? ---
        double gap = currentEqHalf - GOAL_FLAT_SEC;
        double requiredRate = gap <= 0 ? 0 : gap / Math.max(remainingWeeks, 0.3);
        double planRateNow = Math.max(0, expectedNow - GOAL_FLAT_SEC) / Math.max(remainingWeeks, 0.3);
        // Each s/week of extra improvement needed beyond the plan's own line costs 5 points.
        double trajectory = requiredRate <= planRateNow + 0.5 ? 1.0
                : clamp(1 - (requiredRate - planRateNow) / 20.0, 0, 1);

        // --- injury-risk discount ---
        int red = 0, graded = 0;
        Double lastAcwr = null;
        for (ReadinessResult r : readinessService.computeRange(asOf.minusDays(28).toString(), asOfStr)) {
            if (!"NEEDS_DATA".equals(r.verdict())) { graded++; if ("RED".equals(r.verdict())) red++; }
            if (r.acwr() != null) lastAcwr = r.acwr();
        }
        double risk = graded == 0 ? 1.0 : 1 - 0.25 * ((double) red / graded);
        if (lastAcwr != null && lastAcwr > 1.5) risk -= 0.10;
        risk = clamp(risk, 0.70, 1.0);

        // --- combine ---
        double outlook = clamp(adherence * cpScore * trajectory * risk, 0.02, 1.0);
        int pct = (int) Math.round(outlook * 100);

        int elapsedWeeks = (int) Math.round(elapsedDays / 7.0);
        int remWeeks = (int) Math.round(remainingWeeks);

        String band = pct >= 80 ? "ON_TRACK" : pct >= 55 ? "HARD_BUT_LIVE" : pct >= 30 ? "SLIPPING" : "OFF_TRACK";
        String headline = elapsedWeeks < 2
                ? "Barely started — this settles as you build a couple of weeks of history."
                : headline(band, trajectory, volScore, cpScore, lastRatio == null);

        List<GoalProjection.Factor> factors = List.of(
                new GoalProjection.Factor("Volume", (int) Math.round(volScore * 100),
                        String.format("%.0f of %.0f km prescribed (last 4 wks)", actualKm, plannedKm)),
                new GoalProjection.Factor("Key workouts", (int) Math.round(workScore * 100),
                        doneWorkouts + " of " + plannedWorkouts + " quality sessions (last 4 wks)"),
                new GoalProjection.Factor("Execution", (int) Math.round(execScore * 100),
                        scores.isEmpty() ? "no scored sessions yet"
                                : String.format("avg session score %.1f/10 (full marks at 8.5)", execAvg)),
                new GoalProjection.Factor("Checkpoints", (int) Math.round(cpScore * 100),
                        lastCpDetail != null ? lastCpDetail : "none reached yet"),
                new GoalProjection.Factor("Trajectory", (int) Math.round(trajectory * 100),
                        gap <= 0 ? "already at goal fitness"
                                : trajectory >= 1 ? "on the plan's line to the goal"
                                : String.format("need ~%.0f s/wk over %d wks (plan's line asks ~%.0f)", requiredRate, remWeeks, planRateNow)),
                new GoalProjection.Factor("Injury risk", (int) Math.round(risk * 100),
                        graded == 0 ? "no readiness data yet"
                                : red + " RED of " + graded + " logged days (last 4 wks)")
        );

        // Shown as a predicted time on the goal course, so it compares directly with the goal.
        double onGoalCourse = currentEqHalf * GOAL_SEC / GOAL_FLAT_SEC;
        return new GoalProjection(pct, band, headline, fmt(onGoalCourse), fmt(GOAL_SEC),
                elapsedWeeks, remWeeks, factors);
    }

    /**
     * Where the plan expects your flat-equivalent half fitness to be on a date: straight lines from
     * the plan-start baseline through each current-plan checkpoint target to the goal race.
     */
    private static double expectedOnPlan(LocalDate d) {
        List<LocalDate> ds = new ArrayList<>(List.of(PLAN_START));
        List<Double> vs = new ArrayList<>(List.of(BASE_SEC));
        PlanConstants.CHECKPOINTS.entrySet().stream()
                .filter(e -> e.getKey() >= TRAJECTORY_FROM_WEEK)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    PlanConstants.Checkpoint cp = e.getValue();
                    int dayOff = java.util.Arrays.asList(PlanConstants.DAY_NAMES).indexOf(cp.day);
                    ds.add(PlanConstants.WEEK1_START.plusDays((e.getKey() - 1) * 7L + dayOff));
                    vs.add(riegelHalf(cp.distKm, cp.targetSec * cp.courseFactor));
                });
        if (!d.isAfter(ds.get(0))) return vs.get(0);
        for (int i = 1; i < ds.size(); i++) {
            if (!d.isAfter(ds.get(i))) {
                double span = ChronoUnit.DAYS.between(ds.get(i - 1), ds.get(i));
                double f = span <= 0 ? 1 : ChronoUnit.DAYS.between(ds.get(i - 1), d) / span;
                return vs.get(i - 1) + (vs.get(i) - vs.get(i - 1)) * f;
            }
        }
        return vs.get(vs.size() - 1);
    }

    private static String headline(String band, double traj, double vol, double cp, boolean noCp) {
        if ("ON_TRACK".equals(band)) return "Doing what the plan asks — keep this up and 1:40 in May is yours.";
        if (!noCp && cp < 0.8) return "Latest checkpoint came in behind target — hit the next one and this recovers fast.";
        if (vol < 0.85) return "Volume is the main gap — the prescribed kilometres are what move this most.";
        if (traj < 0.5) return "Fitness is behind the plan's line — consistent weeks and the next checkpoint close it.";
        return switch (band) {
            case "HARD_BUT_LIVE" -> "Close — tidy up the missed pieces and this climbs toward 100%.";
            case "SLIPPING" -> "Slipping off the plan. A strong few weeks and the next checkpoint pull it back.";
            default -> "Well off the plan right now — reset with four solid weeks.";
        };
    }

    // predicted half time (sec) from a race of distKm run in timeSec (Riegel, exponent 1.06)
    private static double riegelHalf(double distKm, double timeSec) {
        return timeSec * Math.pow(21.0975 / distKm, 1.06);
    }

    private static String fmt(double sec) {
        int s = (int) Math.round(sec);
        return s / 3600 + ":" + pad(s % 3600 / 60) + ":" + pad(s % 60);
    }
    private static String pad(int n) { return n < 10 ? "0" + n : "" + n; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static long clampL(long v, long lo, long hi) { return Math.max(lo, Math.min(hi, v)); }
}
