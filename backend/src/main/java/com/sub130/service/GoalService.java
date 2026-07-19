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
 * "Goal outlook" — a transparent heuristic (NOT a validated probability) for how likely
 * sub-1:30 is, given training done so far and time remaining.
 *
 *   likelihood = clamp( PRIOR·(1−conf) + conf·(PRIOR · exec · checkpoint · feasibility · risk) )
 *
 *   PRIOR         starting odds for this athlete on this plan (encouraging anchor: 0.55)
 *   exec          adherence + execution quality vs the prescription (recency-weighted)
 *   checkpoint    race results vs the plan's target times (ground truth once they happen)
 *   feasibility   can the remaining weeks still deliver the improvement still needed?
 *                 -> collapses toward 0 when the required rate stops being plausible
 *   risk          injury/overreaching discount from readiness (RED days, high ACWR)
 *   conf          grows with elapsed time so early noise doesn't swing it (responsive: min 0.35)
 */
@Service
public class GoalService {

    private static final LocalDate PLAN_START = LocalDate.of(2026, 7, 17);
    private static final int PLAN_DAYS = 364;
    private static final double GOAL_SEC = 5400;      // 1:30:00
    private static final double BASE_SEC = 6480;      // 1:48:00 starting fitness
    private static final double PRIOR = 0.55;         // encouraging anchor
    private static final double PLAN_RATE = (BASE_SEC - GOAL_SEC) / 52.0;   // ~20.8 s/week the plan demands
    private static final double INFEASIBLE_RATE = 34.0;                     // s/week that's not plausible

    private final PlanService planService;
    private final DayLogRepository logRepo;
    private final ScoreService scoreService;
    private final ReadinessService readinessService;

    public GoalService(PlanService planService, DayLogRepository logRepo,
                       ScoreService scoreService, ReadinessService readinessService) {
        this.planService = planService;
        this.logRepo = logRepo;
        this.scoreService = scoreService;
        this.readinessService = readinessService;
    }

    public GoalProjection project(LocalDate asOf) {
        long elapsedDays = clampL(ChronoUnit.DAYS.between(PLAN_START, asOf), 0, PLAN_DAYS);
        double p = (double) elapsedDays / PLAN_DAYS;
        double remainingWeeks = Math.max((PLAN_DAYS - elapsedDays) / 7.0, 0.0);
        String asOfStr = asOf.toString();

        Map<String, DayLog> logs = new HashMap<>();
        for (DayLog l : logRepo.findAllByOrderByDateAsc()) logs.put(l.date, l);
        List<PlannedSession> plan = planService.all();

        // --- adherence (completed days only: strictly before today) ---
        double plannedKmAll = 0, actualKmAll = 0, plannedKmRecent = 0, actualKmRecent = 0;
        int plannedWorkouts = 0, doneWorkouts = 0;
        List<Double> scores = new ArrayList<>();
        String recentCutoff = asOf.minusDays(28).toString();

        for (PlannedSession s : plan) {
            if (s.date.compareTo(asOfStr) > 0) continue;          // future doesn't count
            DayLog log = logs.get(s.date);
            boolean done = log != null && log.done && log.actualKm != null;
            // Today counts only once it's actually done, so an unfinished day isn't a "miss".
            if (s.date.equals(asOfStr) && !done) continue;
            if (s.plannedKm != null) {
                plannedKmAll += s.plannedKm;
                if (done) actualKmAll += log.actualKm;
                if (s.date.compareTo(recentCutoff) >= 0) {
                    plannedKmRecent += s.plannedKm;
                    if (done) actualKmRecent += log.actualKm;
                }
            }
            if ("Workout".equals(s.type)) { plannedWorkouts++; if (done) doneWorkouts++; }
            if (done) {
                Double sc = scoreService.score(log).score();
                if (sc != null) scores.add(sc);
            }
        }

        double volCum = plannedKmAll > 0 ? actualKmAll / plannedKmAll : 1.0;
        double volRecent = plannedKmRecent > 0 ? actualKmRecent / plannedKmRecent : volCum;
        double volAdh = elapsedDays >= 28 ? 0.6 * volRecent + 0.4 * volCum : volCum;   // responsive: weight recent
        double workAdh = plannedWorkouts > 0 ? (double) doneWorkouts / plannedWorkouts : 1.0;
        double execQ = scores.isEmpty() ? 0.85 : scores.stream().mapToDouble(x -> x).average().orElse(8.5) / 10.0;

        double ti = 0.45 * Math.min(1, volAdh) + 0.35 * Math.min(1, workAdh) + 0.20 * execQ;
        double execMult = clamp(ti / 0.90, 0.30, 1.15);

        // --- checkpoints: race pace vs target pace (ground truth) ---
        List<Double> ratios = new ArrayList<>();
        Double lastCpEqHalf = null;
        String lastCpDetail = null;
        for (PlannedSession s : plan) {
            if (!s.checkpoint || s.date.compareTo(asOfStr) > 0) continue;   // include today if raced
            PlanConstants.Checkpoint cp = PlanConstants.CHECKPOINTS.get(s.week);
            DayLog log = logs.get(s.date);
            if (cp == null || log == null || !log.done || log.actualKm == null
                    || log.actualMinutes == null || log.actualKm <= 0) continue;
            double measuredPace = log.actualMinutes * 60.0 / log.actualKm;   // s/km
            double targetPace = cp.targetSec / cp.distKm;
            double ratio = targetPace / measuredPace;                        // >1 = faster than target
            ratios.add(ratio);
            lastCpEqHalf = riegelHalf(cp.distKm, measuredPace * cp.distKm);
            lastCpDetail = cp.label + ": " + fmt(measuredPace * cp.distKm) + " vs " + cp.targetLabel + " target ("
                    + (ratio >= 1 ? "ahead" : "behind") + ")";
        }
        double checkpointMult = ratios.isEmpty() ? 1.0
                : clamp(1 + (avg(ratios) - 1) * 6, 0.5, 1.5);

        // --- current fitness estimate (equivalent half time) ---
        double effectiveness = clamp(ti, 0.15, 1.05);
        double modelEqHalf = BASE_SEC - (BASE_SEC - GOAL_SEC) * p * effectiveness;
        double currentEqHalf = lastCpEqHalf != null ? 0.7 * lastCpEqHalf + 0.3 * modelEqHalf : modelEqHalf;
        currentEqHalf = clamp(currentEqHalf, 5100, 6600);

        // --- feasibility ceiling ---
        double gap = currentEqHalf - GOAL_SEC;
        double requiredRate = gap <= 0 ? 0 : gap / Math.max(remainingWeeks, 0.3);
        double feasibility = gap <= 0 ? 1.0
                : clamp((INFEASIBLE_RATE - requiredRate) / (INFEASIBLE_RATE - PLAN_RATE), 0, 1);

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
        double raw = PRIOR * execMult * checkpointMult * feasibility * risk;
        double conf = clamp(elapsedDays / 42.0, 0.35, 1.0);           // responsive
        double likelihood = clamp(PRIOR * (1 - conf) + raw * conf, 0.02, 0.95);
        int pct = (int) Math.round(likelihood * 100);

        int elapsedWeeks = (int) Math.round(elapsedDays / 7.0);
        int remWeeks = (int) Math.round(remainingWeeks);

        String band = pct >= 50 ? "ON_TRACK" : pct >= 32 ? "HARD_BUT_LIVE" : pct >= 15 ? "SLIPPING" : "OFF_TRACK";
        String headline = elapsedWeeks < 2
                ? "Barely started — this settles as you build a couple of weeks of history."
                : headline(band, feasibility, volAdh, checkpointMult, ratios.isEmpty());

        List<GoalProjection.Factor> factors = List.of(
                new GoalProjection.Factor("Volume", (int) Math.round(Math.min(1.2, volAdh) * 100),
                        String.format("%.0f of %.0f km prescribed so far", actualKmAll, plannedKmAll)),
                new GoalProjection.Factor("Key workouts", (int) Math.round(workAdh * 100),
                        doneWorkouts + " of " + plannedWorkouts + " quality sessions done"),
                new GoalProjection.Factor("Execution", (int) Math.round(execQ * 100),
                        scores.isEmpty() ? "no scored sessions yet"
                                : String.format("avg session score %.1f/10", execQ * 10)),
                new GoalProjection.Factor("Checkpoints", ratios.isEmpty() ? 50 : (int) Math.round(clamp(avg(ratios), 0.7, 1.3) / 1.3 * 100),
                        lastCpDetail != null ? lastCpDetail : "none reached yet"),
                new GoalProjection.Factor("Time left", (int) Math.round(feasibility * 100),
                        gap <= 0 ? "already at goal fitness"
                                : String.format("need ~%.0f s/wk over %d wks (plan asks ~%.0f)", requiredRate, remWeeks, PLAN_RATE)),
                new GoalProjection.Factor("Injury risk", (int) Math.round(risk * 100),
                        graded == 0 ? "no readiness data yet"
                                : red + " RED of " + graded + " logged days (last 4 wks)")
        );

        return new GoalProjection(pct, band, headline, fmt(currentEqHalf), "1:29:59",
                elapsedWeeks, remWeeks, factors);
    }

    private static String headline(String band, double feas, double vol, double cpMult, boolean noCp) {
        if (feas < 0.30) return "Time is getting short for the pace you're at — this is the hard truth the model exists to show.";
        if (vol < 0.80) return "Missed volume is the main drag. Consistency from here is what moves this most.";
        if (!noCp && cpMult > 1.10) return "You're beating your checkpoint targets — the strongest signal there is.";
        if (!noCp && cpMult < 0.92) return "Checkpoint result came in behind target — the 18-month path may be the smarter call.";
        return switch (band) {
            case "ON_TRACK" -> "Training is tracking the plan. Keep the easy days easy and hit the key sessions.";
            case "HARD_BUT_LIVE" -> "Doable, but it's the hard path — exactly as billed from a 1:48 start.";
            case "SLIPPING" -> "Slipping off the pace. A strong block over the next few weeks can pull it back.";
            default -> "The 12-month goal is off track. Extending to ~18 months is a success, not a failure.";
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
    private static double avg(List<Double> xs) { return xs.stream().mapToDouble(x -> x).average().orElse(1.0); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static long clampL(long v, long lo, long hi) { return Math.max(lo, Math.min(hi, v)); }
}
