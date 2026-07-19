package com.sub130.service;

import com.sub130.domain.DayLog;
import com.sub130.dto.DaySummary;
import com.sub130.dto.ReadinessResult;
import com.sub130.dto.ScoreResult;
import com.sub130.dto.WeekSummary;
import com.sub130.plan.PlannedSession;
import com.sub130.repo.DayLogRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Merges the four services into the rows and rollups the frontend charts consume. */
@Service
public class AnalyticsService {

    private final PlanService planService;
    private final DayLogRepository logRepo;
    private final ReadinessService readinessService;
    private final ScoreService scoreService;

    public AnalyticsService(PlanService planService, DayLogRepository logRepo,
                            ReadinessService readinessService, ScoreService scoreService) {
        this.planService = planService;
        this.logRepo = logRepo;
        this.readinessService = readinessService;
        this.scoreService = scoreService;
    }

    public DaySummary summaryFor(String date) {
        PlannedSession p = planService.forDate(date);
        DayLog log = logRepo.findById(date).orElse(null);
        if (p == null && log == null) return null;      // truly nothing here
        ReadinessResult r = readinessService.compute(date);
        if (p == null) {                                // off-plan day, but data was logged
            return merge(offDay(date), log, r, null);
        }
        ScoreResult sc = log == null ? null : scoreService.score(log);
        return merge(p, log, r, sc);
    }

    /** A placeholder "session" for dates outside the plan that nonetheless carry a log. */
    private PlannedSession offDay(String date) {
        PlannedSession s = new PlannedSession();
        s.date = date;
        s.week = 0;
        s.phase = 0;
        s.phaseName = "";
        s.dayName = java.time.LocalDate.parse(date)
                .getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US);
        s.type = "Off";
        s.session = "No scheduled training";
        s.paceRange = "-";
        s.mphRange = "-";
        s.incline = "-";
        return s;
    }

    public List<DaySummary> summaries(String start, String end) {
        Map<String, DayLog> logs = new HashMap<>();
        for (DayLog l : logRepo.findInDateRange(start, end)) logs.put(l.date, l);
        Map<String, ReadinessResult> ready = new HashMap<>();
        for (ReadinessResult r : readinessService.computeRange(start, end)) ready.put(r.date(), r);

        List<DaySummary> out = new ArrayList<>();
        for (PlannedSession p : planService.all()) {
            if (p.date.compareTo(start) < 0 || p.date.compareTo(end) > 0) continue;
            DayLog log = logs.get(p.date);
            ScoreResult sc = log == null ? null : scoreService.score(log);
            out.add(merge(p, log, ready.get(p.date), sc));
        }
        out.sort((a, b) -> a.date().compareTo(b.date()));
        return out;
    }

    public List<WeekSummary> weekly() {
        List<PlannedSession> all = planService.all();
        Map<String, DayLog> logs = new HashMap<>();
        for (DayLog l : logRepo.findAllByOrderByDateAsc()) logs.put(l.date, l);

        Map<Integer, List<PlannedSession>> byWeek = new java.util.TreeMap<>();
        for (PlannedSession p : all) byWeek.computeIfAbsent(p.week, k -> new ArrayList<>()).add(p);

        List<WeekSummary> out = new ArrayList<>();
        for (var e : byWeek.entrySet()) {
            List<PlannedSession> ws = e.getValue();
            double plannedKm = 0, actualKm = 0, scoreSum = 0;
            int planned = 0, done = 0, scoreCount = 0;
            String checkpoint = null;
            for (PlannedSession p : ws) {
                if (p.plannedKm != null) { plannedKm += p.plannedKm; planned++; }
                if (p.checkpoint) checkpoint = p.session.split(" - ")[0];
                DayLog log = logs.get(p.date);
                if (log != null && log.done) {
                    done++;
                    if (log.actualKm != null) actualKm += log.actualKm;
                    ScoreResult sc = scoreService.score(log);
                    if (sc.score() != null) { scoreSum += sc.score(); scoreCount++; }
                }
            }
            String dates = ws.get(0).date + " to " + ws.get(ws.size() - 1).date;
            out.add(new WeekSummary(e.getKey(), ws.get(0).phaseName, dates,
                    round1(plannedKm), round1(actualKm), planned, done,
                    scoreCount == 0 ? null : round1(scoreSum / scoreCount), checkpoint));
        }
        return out;
    }

    private DaySummary merge(PlannedSession p, DayLog log, ReadinessResult r, ScoreResult sc) {
        return new DaySummary(
                p.date, p.week, p.dayName, p.type, p.phaseName, p.session, p.plannedKm, p.estMinutes,
                p.paceRange, p.mphRange, p.hrZone, p.targetHrBpm, p.incline, p.checkpoint,
                log != null && log.done,
                log == null ? null : log.source,
                log == null ? null : log.actualKm,
                log == null ? null : log.actualMinutes,
                log == null ? null : log.avgHr,
                log == null ? null : log.notes,
                log == null ? null : log.hrvMs,
                log == null ? null : log.restingHr,
                log == null ? null : log.sleepScore,
                r == null ? null : r.hrvPctOfBase(),
                r == null ? null : r.load(),
                r == null ? null : r.acwr(),
                r == null ? null : r.verdict(),
                r == null ? null : r.action(),
                sc == null ? null : sc.score(),
                sc == null ? null : sc.grade(),
                sc == null ? null : sc.headline());
    }

    private static Double round1(double x) { return Math.round(x * 10) / 10.0; }
}
