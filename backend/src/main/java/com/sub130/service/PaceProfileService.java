package com.sub130.service;

import com.sub130.domain.DayLog;
import com.sub130.dto.PaceProfile;
import com.sub130.dto.PaceProfile.Pt;
import com.sub130.plan.PlanConstants;
import com.sub130.plan.PlannedSession;
import com.sub130.repo.DayLogRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the planned pace profile for a session (a step function over time: easy stretches,
 * reps, recoveries, strides) and pairs it with the actual pace recovered from the Strava
 * speed series, both sampled on a common 10-second grid for the chart.
 */
@Service
public class PaceProfileService {

    private static final int GRID_S = 10;
    private static final int STRIDE_S = 20;       // a 100m stride is ~20 s
    private static final int STRIDE_PACE = 230;   // ~3:50/km target
    private static final int STRIDE_REC_S = 45;   // walk/jog between strides

    private static final Pattern INTERVAL = Pattern.compile("(\\d+)\\s*x\\s*(\\d+(?:\\.\\d+)?)\\s*(min|km|m)\\b\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern RECOVERY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(s|min|m)\\s*jog");
    private static final Pattern HILL = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)\\s*s\\s*hill sprints");
    private static final Pattern STRIDES = Pattern.compile("(\\d+)\\s*x\\s*100m strides");
    private static final Pattern SINGLE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*min\\s*@\\s*(\\d:\\d{2})/km");
    private static final Pattern KM = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*km");

    private final PlanService planService;
    private final DayLogRepository logRepo;

    public PaceProfileService(PlanService planService, DayLogRepository logRepo) {
        this.planService = planService;
        this.logRepo = logRepo;
    }

    private record Seg(double dur, int pace) {}

    public PaceProfile build(String date) {
        PlannedSession p = planService.forDate(date);
        if (p == null || "Rest".equals(p.type) || "Off".equals(p.type))
            return new PaceProfile(date, "", false, List.of());

        int phase = p.phase == 0 ? 1 : p.phase;
        int[] e = PlanConstants.easy(phase);
        int easy = (e[0] + e[1]) / 2;
        String raw = p.rawSession != null ? p.rawSession : p.session;

        List<Seg> segs = new ArrayList<>();
        buildSegments(p, raw, easy, phase, segs);

        DayLog log = logRepo.findById(date).orElse(null);
        List<Double> speed = log == null ? null : log.speedSeries;
        boolean hasActual = speed != null && !speed.isEmpty();

        int plannedEnd = (int) Math.ceil(segs.stream().mapToDouble(Seg::dur).sum());
        int actualEnd = hasActual ? speed.size() * GRID_S : 0;
        int end = Math.max(plannedEnd, actualEnd);
        if (end == 0) return new PaceProfile(date, title(p), false, List.of());

        List<Pt> pts = new ArrayList<>();
        for (int t = 0; t <= end; t += GRID_S) {
            Integer planned = sampleSeg(segs, t);
            Integer actual = null;
            if (hasActual) {
                int idx = t / GRID_S;
                if (idx < speed.size()) {
                    double v = speed.get(idx);
                    actual = v > 0.5 ? (int) Math.round(1000.0 / v) : null; // ignore standing still
                }
            }
            pts.add(new Pt(t, planned, actual));
        }
        return new PaceProfile(date, title(p), hasActual, pts);
    }

    /** Pace of the segment covering time t, or null once past the planned end. */
    private Integer sampleSeg(List<Seg> segs, int t) {
        double acc = 0;
        for (Seg s : segs) {
            if (t < acc + s.dur) return s.pace;
            acc += s.dur;
        }
        return null;
    }

    private void buildSegments(PlannedSession p, String raw, int easy, int phase, List<Seg> segs) {
        switch (p.type) {
            case "Easy", "Long" -> { easyBase(p, easy, segs); strides(raw, easy, segs); }
            case "RACE" -> {
                double wu = p.plannedKm != null && p.plannedKm > 18 ? 1 : 2;
                segs.add(new Seg(wu * easy, easy));
                Matcher m = Pattern.compile("(\\d:\\d{2})/km").matcher(p.paceRange);
                if (m.find()) {
                    int pace = paceSec(m.group(1));
                    double km = p.plannedKm != null ? p.plannedKm : 10;
                    segs.add(new Seg(km * pace, pace));
                }
                segs.add(new Seg(wu * easy, easy));
            }
            default -> workout(p, raw, easy, phase, segs);
        }
    }

    private void workout(PlannedSession p, String raw, int easy, int phase, List<Seg> segs) {
        Matcher hillM = HILL.matcher(raw);
        Matcher ivM = INTERVAL.matcher(raw);
        Matcher singleM = SINGLE.matcher(raw);
        boolean hasHill = hillM.find();
        boolean hasIv = ivM.find();
        boolean hasSingle = !hasIv && singleM.find();

        if (!hasHill && !hasIv && !hasSingle) {   // just an easy run + strides
            easyBase(p, easy, segs);
            strides(raw, easy, segs);
            return;
        }

        segs.add(new Seg(firstKm(p.warmup, 3) * easy, easy));   // warm-up jog
        if (hasHill) {
            int n = Integer.parseInt(hillM.group(1));
            int sec = Integer.parseInt(hillM.group(2));
            int hard = PlanConstants.PACE_SEC.get("vo2")[phase];   // nominal fast pace for a sprint
            for (int i = 0; i < n; i++) {
                segs.add(new Seg(sec, hard));
                if (i < n - 1) segs.add(new Seg(90, easy));        // full recovery
            }
        } else if (hasIv) {
            int n = Integer.parseInt(ivM.group(1));
            double amt = Double.parseDouble(ivM.group(2));
            String unit = ivM.group(3);
            int pace = paceSec(ivM.group(4));
            double repDur = unit.equals("min") ? amt * 60 : unit.equals("km") ? amt * pace : amt / 1000.0 * pace;
            double recDur = recoveryS(raw);
            for (int i = 0; i < n; i++) {
                segs.add(new Seg(repDur, pace));
                if (i < n - 1) segs.add(new Seg(recDur, easy));
            }
        } else {
            int pace = paceSec(singleM.group(2));
            segs.add(new Seg(Double.parseDouble(singleM.group(1)) * 60, pace));
        }
        strides(raw, easy, segs);
        segs.add(new Seg(firstKm(p.cooldown, 2) * easy, easy));  // cool-down jog
    }

    private void easyBase(PlannedSession p, int easy, List<Seg> segs) {
        if (p.plannedKm != null) segs.add(new Seg(p.plannedKm * easy, easy));
    }

    private void strides(String raw, int easy, List<Seg> segs) {
        Matcher st = STRIDES.matcher(raw);
        if (!st.find()) return;
        int n = Integer.parseInt(st.group(1));
        for (int i = 0; i < n; i++) {
            segs.add(new Seg(STRIDE_S, STRIDE_PACE));
            if (i < n - 1) segs.add(new Seg(STRIDE_REC_S, easy));
        }
    }

    private double recoveryS(String raw) {
        Matcher r = RECOVERY.matcher(raw);
        if (!r.find()) return 60;
        double v = Double.parseDouble(r.group(1));
        return switch (r.group(2)) { case "min" -> v * 60; case "m" -> v; default -> v; };
    }

    private double firstKm(String s, double dflt) {
        if (s == null) return dflt;
        Matcher m = KM.matcher(s);
        return m.find() ? Double.parseDouble(m.group(1)) : dflt;
    }

    private static int paceSec(String pace) {
        String[] a = pace.split(":");
        return Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
    }

    private String title(PlannedSession p) {
        return switch (p.type) {
            case "RACE" -> "Race day"; case "Long" -> "Long run";
            case "Easy" -> "Easy run"; default -> "Workout";
        };
    }
}
