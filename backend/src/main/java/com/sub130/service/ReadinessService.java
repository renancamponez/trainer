package com.sub130.service;

import com.sub130.domain.DayLog;
import com.sub130.dto.ReadinessResult;
import com.sub130.repo.DayLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Morning readiness from HRV, resting HR and sleep score (synced daily from Garmin).
 *
 * All windows are calendar-day windows; missing values are skipped:
 *   - HRV / RHR baselines : average of the prior 7 days (need >=4 readings each)
 *   - Sub-scores (0-1)    : HRV vs baseline (100% -> 1, 90% -> 0.5, <=80% -> 0), resting HR rise over
 *                           baseline (+0 -> 1, >=+7 bpm -> 0), sleep score (85 -> 1, <=35 -> 0)
 *   - Readiness 0-100     : weighted HRV 50 / RHR 30 / sleep 20 over whatever is present
 *   - Verdict             : GREEN >=66 | AMBER 45-65 | RED <45
 *   - Load / ACWR         : km-based acute (7 d) vs chronic (28 d / 4) load
 * DailySessionService turns the verdict into the day's adjusted session.
 */
@Service
public class ReadinessService {

    private final DayLogRepository repo;

    public ReadinessService(DayLogRepository repo) { this.repo = repo; }

    public ReadinessResult compute(String date) {
        LocalDate d = LocalDate.parse(date);
        List<DayLog> window = repo.findInDateRange(d.minusDays(28).toString(), date);
        return compute(date, index(window));
    }

    public List<ReadinessResult> computeRange(String start, String end) {
        LocalDate from = LocalDate.parse(start).minusDays(28);
        Map<LocalDate, DayLog> byDate = index(
                repo.findInDateRange(from.toString(), end));
        return byDate.keySet().stream()
                .filter(k -> !k.isBefore(LocalDate.parse(start)) && !k.isAfter(LocalDate.parse(end)))
                .sorted()
                .map(k -> compute(k.toString(), byDate))
                .collect(Collectors.toList());
    }

    private Map<LocalDate, DayLog> index(List<DayLog> logs) {
        Map<LocalDate, DayLog> m = new TreeMap<>();
        for (DayLog l : logs) m.put(LocalDate.parse(l.date), l);
        return m;
    }

    private ReadinessResult compute(String date, Map<LocalDate, DayLog> byDate) {
        LocalDate d = LocalDate.parse(date);
        DayLog today = byDate.get(d);

        Double hrv = today == null ? null : today.hrvMs;
        Integer rhr = today == null ? null : today.restingHr;
        Integer sleep = today == null ? null : today.sleepScore;
        Double load = today == null ? null : today.load();

        // Personal baselines: rolling average of the prior 7 days (need >=4 readings)
        List<Double> priorHrv = collect(byDate, d.minusDays(7), d.minusDays(1), l -> l.hrvMs);
        Double hrvBase = priorHrv.size() >= 4 ? round1(avg(priorHrv)) : null;
        List<Double> priorRhr = collect(byDate, d.minusDays(7), d.minusDays(1),
                l -> l.restingHr == null ? null : (double) l.restingHr);
        Double rhrBase = priorRhr.size() >= 4 ? round1(avg(priorRhr)) : null;

        Integer hrvPct = (hrv != null && hrvBase != null && hrvBase > 0)
                ? (int) Math.round(100 * hrv / hrvBase) : null;

        // Per-signal readiness sub-scores (0-1). Each only counts if its inputs exist.
        //  HRV : ratio to baseline, 1.00 -> 1.0, 0.90 -> 0.5, <=0.80 -> 0. Day-to-day HRV swings ~10%,
        //        so one mildly low reading on its own shouldn't cancel a session.
        //  RHR : rise over baseline is bad; +0 -> 1.0, +3.5 -> 0.5, >=+7 -> 0
        //  Sleep: Garmin score (absolute); 85 -> 1.0, 60 -> 0.5, <=35 -> 0
        Double sHrv = (hrv != null && hrvBase != null && hrvBase > 0)
                ? clamp((hrv / hrvBase - 0.80) / 0.20, 0, 1) : null;
        Double sRhr = (rhr != null && rhrBase != null)
                ? clamp(1 - Math.max(0, rhr - rhrBase) / 7.0, 0, 1) : null;
        Double sSleep = (sleep != null) ? clamp((sleep - 35) / 50.0, 0, 1) : null;

        // Weighted composite over whatever is available (HRV 50 / RHR 30 / Sleep 20).
        double wSum = 0, sSum = 0;
        if (sHrv != null)   { wSum += 0.5; sSum += 0.5 * sHrv; }
        if (sRhr != null)   { wSum += 0.3; sSum += 0.3 * sRhr; }
        if (sSleep != null) { wSum += 0.2; sSum += 0.2 * sSleep; }
        Integer score = wSum > 0 ? (int) Math.round(100 * sSum / wSum) : null;

        // Load / ACWR (unchanged)
        List<Double> acuteLoads = collect(byDate, d.minusDays(6), d, DayLog::load);
        Double acute = acuteLoads.isEmpty() ? null : sum(acuteLoads);
        List<Double> chronicLoads = collect(byDate, d.minusDays(27), d, DayLog::load);
        Double chronic = chronicLoads.size() >= 14 ? round0(sum(chronicLoads) / 4) : null;
        Double acwr = (acute != null && chronic != null && chronic > 0) ? round2(acute / chronic) : null;

        String verdict, action;
        if (score == null) {
            verdict = "NEEDS_DATA";
            action = "Log HRV, resting HR and sleep score to unlock readiness.";
        } else {
            List<String> drivers = new java.util.ArrayList<>();
            if (sHrv != null && sHrv < 0.6) drivers.add("HRV " + hrvPct + "% of usual");
            if (sRhr != null && sRhr < 0.6) drivers.add("resting HR +" + (int) Math.round(rhr - rhrBase) + " bpm");
            if (sSleep != null && sSleep < 0.6) drivers.add("sleep score " + sleep);
            String why = drivers.isEmpty() ? "" : " (" + String.join(", ", drivers) + ")";
            if (score < 45) {
                verdict = "RED";
                action = "STOP: swap today for easy 30-40min or full rest" + why + ".";
            } else if (score < 66) {
                verdict = "AMBER";
                action = "EASE OFF: run easy or halve the reps" + why + ".";
            } else if (acwr != null && acwr > 1.5) {
                verdict = "GREEN";
                action = "Load spike (ACWR>1.5): train as planned but hold volume flat this week.";
            } else {
                verdict = "GREEN";
                action = "Train as planned.";
            }
        }

        return new ReadinessResult(date, score, hrv, hrvBase, hrvPct, rhr, rhrBase, sleep,
                load, acute, chronic, acwr, verdict, action);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private interface Getter { Double get(DayLog l); }

    private List<Double> collect(Map<LocalDate, DayLog> byDate, LocalDate from, LocalDate to, Getter g) {
        var out = new java.util.ArrayList<Double>();
        for (LocalDate x = from; !x.isAfter(to); x = x.plusDays(1)) {
            DayLog l = byDate.get(x);
            if (l == null) continue;
            Double v = g.get(l);
            if (v != null) out.add(v);
        }
        return out;
    }

    private static double avg(List<Double> xs) { return sum(xs) / xs.size(); }
    private static double sum(List<Double> xs) { double s = 0; for (double x : xs) s += x; return s; }
    private static Double round0(double x) { return (double) Math.round(x); }
    private static Double round1(double x) { return Math.round(x * 10) / 10.0; }
    private static Double round2(double x) { return Math.round(x * 100) / 100.0; }
}
