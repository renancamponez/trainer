package com.sub130.web;

import com.sub130.dto.PaceProfile;
import com.sub130.dto.WorkoutDetail;
import com.sub130.plan.PlannedSession;
import com.sub130.service.DailySessionService;
import com.sub130.service.PaceProfileService;
import com.sub130.service.PlanService;
import com.sub130.service.WorkoutService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;
    private final WorkoutService workoutService;
    private final PaceProfileService paceProfileService;
    private final DailySessionService dailySessionService;

    public PlanController(PlanService planService, WorkoutService workoutService,
                          PaceProfileService paceProfileService, DailySessionService dailySessionService) {
        this.planService = planService;
        this.workoutService = workoutService;
        this.paceProfileService = paceProfileService;
        this.dailySessionService = dailySessionService;
    }

    /** Step-by-step breakdown of a day's session (as adjusted by that morning's readiness). */
    @GetMapping("/workout/{date}")
    public WorkoutDetail workout(@PathVariable String date) {
        return workoutService.expand(dailySessionService.effective(date));
    }

    /** Planned-vs-actual pace over the workout timeline (actual from the Strava speed series). */
    @GetMapping("/profile/{date}")
    public PaceProfile profile(@PathVariable String date) {
        return paceProfileService.build(date);
    }

    @GetMapping
    public List<PlannedSession> all() { return planService.all(); }

    @GetMapping("/week/{week}")
    public List<PlannedSession> week(@PathVariable int week) { return planService.week(week); }

    @GetMapping("/date/{date}")
    public PlannedSession forDate(@PathVariable String date) { return dailySessionService.effective(date); }
}
