package com.sub130.web;

import com.sub130.dto.DaySummary;
import com.sub130.dto.GoalProjection;
import com.sub130.dto.ReadinessResult;
import com.sub130.dto.WeekSummary;
import com.sub130.service.AnalyticsService;
import com.sub130.service.GoalService;
import com.sub130.service.ReadinessService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final ReadinessService readiness;
    private final GoalService goal;

    public AnalyticsController(AnalyticsService analytics, ReadinessService readiness, GoalService goal) {
        this.analytics = analytics;
        this.readiness = readiness;
        this.goal = goal;
    }

    /** Heuristic goal outlook. `asOf` defaults to the server date; the UI passes the user's local date. */
    @GetMapping("/goal")
    public GoalProjection goal(@RequestParam(required = false) String asOf) {
        return goal.project(asOf != null ? LocalDate.parse(asOf) : LocalDate.now());
    }

    /** Merged plan+log+readiness+score for a date range (defaults to the whole plan). */
    @GetMapping("/summary")
    public List<DaySummary> summary(
            @RequestParam(defaultValue = "2026-07-17") String start,
            @RequestParam(defaultValue = "2027-07-18") String end) {
        return analytics.summaries(start, end);
    }

    @GetMapping("/summary/{date}")
    public DaySummary day(@PathVariable String date) { return analytics.summaryFor(date); }

    @GetMapping("/weekly")
    public List<WeekSummary> weekly() { return analytics.weekly(); }

    @GetMapping("/readiness/{date}")
    public ReadinessResult readiness(@PathVariable String date) { return readiness.compute(date); }

    @GetMapping("/readiness")
    public List<ReadinessResult> readinessRange(
            @RequestParam(defaultValue = "2026-07-17") String start,
            @RequestParam(defaultValue = "2027-07-18") String end) {
        return readiness.computeRange(start, end);
    }
}
