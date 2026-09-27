package com.sub130.web;

import com.sub130.domain.DayLog;
import com.sub130.dto.ScoreResult;
import com.sub130.repo.DayLogRepository;
import com.sub130.service.ScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final DayLogRepository repo;
    private final ScoreService scoreService;

    public LogController(DayLogRepository repo, ScoreService scoreService) {
        this.repo = repo;
        this.scoreService = scoreService;
    }

    @GetMapping
    public List<DayLog> all() { return repo.findAllByOrderByDateAsc(); }

    @GetMapping("/{date}")
    public ResponseEntity<DayLog> get(@PathVariable String date) {
        return repo.findById(date).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Upsert a day's log. The date in the path is authoritative. */
    @PutMapping("/{date}")
    public DayLog upsert(@PathVariable String date, @RequestBody DayLog body) {
        body.date = date;
        // Provenance follows the OBJECTIVE actuals only (km / minutes / avg HR). Editing just
        // the morning readiness (HRV, resting HR, sleep) leaves the source alone, so a
        // Strava-synced day stays "strava". Only a change to km/minutes/HR makes it "manual".
        DayLog existing = repo.findById(date).orElse(null);
        boolean hasObjectives = body.actualKm != null || body.actualMinutes != null || body.avgHr != null;
        boolean objectivesUnchanged = existing != null
                && java.util.Objects.equals(existing.actualKm, body.actualKm)
                && java.util.Objects.equals(existing.actualMinutes, body.actualMinutes)
                && java.util.Objects.equals(existing.avgHr, body.avgHr);
        if (!hasObjectives) {
            body.source = existing != null ? existing.source : null;   // readiness-only entry
        } else if (objectivesUnchanged && existing != null && existing.source != null) {
            body.source = existing.source;                             // objectives untouched
        } else {
            body.source = "manual";                                    // km/min/HR set or changed
        }
        // Merge, don't replace: the page never sends the Strava stream data, and a readiness field
        // it leaves empty may have been filled by the Garmin sync after the page was opened.
        if (existing != null) {
            if (body.reps == null) body.reps = existing.reps;
            if (body.speedSeries == null) body.speedSeries = existing.speedSeries;
            if (body.treadmill == null) body.treadmill = existing.treadmill;
            if (body.hrvMs == null) body.hrvMs = existing.hrvMs;
            if (body.restingHr == null) body.restingHr = existing.restingHr;
            if (body.sleepScore == null) body.sleepScore = existing.sleepScore;
        }
        return repo.save(body);
    }

    @DeleteMapping("/{date}")
    public void delete(@PathVariable String date) { repo.deleteById(date); }

    /** Score a date's logged session on demand. */
    @GetMapping("/{date}/score")
    public ScoreResult score(@PathVariable String date) {
        DayLog log = repo.findById(date).orElseGet(() -> new DayLog(date));
        return scoreService.score(log);
    }
}
