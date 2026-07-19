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
 * Autoregulation engine, ported from the spreadsheet's Readiness sheet.
 *
 * All windows are CALENDAR-day windows (one row per day in the sheet), and blanks
 * are ignored exactly as AVERAGE/COUNT/SUM ignore empty cells:
 *   - HRV baseline : average of HRV over the prior 7 days (needs >=4 readings)
 *   - HRV % of base: today / baseline
 *   - Load         : distance run (km-based session load)
 *   - Acute load   : sum of load over the last 7 days
 *   - Chronic load : sum of load over the last 28 days / 4 (needs >=14 load-days)
 *   - ACWR         : acute / chronic
 *   - Verdict      : GREEN >=95% | AMBER 90-95% | RED <90%
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
        Double load = today == null ? null : today.load();

        // HRV baseline: prior 7 days, need at least 4 readings
        List<Double> priorHrv = collect(byDate, d.minusDays(7), d.minusDays(1), l -> l.hrvMs);
        Double baseline = priorHrv.size() >= 4 ? round1(avg(priorHrv)) : null;

        Integer pct = (hrv != null && baseline != null && baseline > 0)
                ? (int) Math.round(100 * hrv / baseline) : null;

        // Acute load: last 7 days (incl today)
        List<Double> acuteLoads = collect(byDate, d.minusDays(6), d, DayLog::load);
        Double acute = acuteLoads.isEmpty() ? null : sum(acuteLoads);

        // Chronic load: last 28 days / 4, need >=14 load-days
        List<Double> chronicLoads = collect(byDate, d.minusDays(27), d, DayLog::load);
        Double chronic = chronicLoads.size() >= 14 ? round0(sum(chronicLoads) / 4) : null;

        Double acwr = (acute != null && chronic != null && chronic > 0)
                ? round2(acute / chronic) : null;

        String verdict, action;
        if (pct == null) {
            verdict = "NEEDS_DATA";
            action = "Train as planned (log HRV daily to unlock readiness).";
        } else if (pct < 90) {
            verdict = "RED";
            action = "STOP: swap today for easy 30-40min or full rest. Do not do the workout.";
        } else if (pct < 95) {
            verdict = "AMBER";
            action = "EASE OFF: run the session at easy pace, or halve the reps.";
        } else if (acwr != null && acwr > 1.5) {
            verdict = "GREEN";
            action = "Load spike (ACWR>1.5): train as planned but hold volume flat this week.";
        } else {
            verdict = "GREEN";
            action = "Train as planned.";
        }

        return new ReadinessResult(date, hrv, baseline, pct, load, acute, chronic, acwr, verdict, action);
    }

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
