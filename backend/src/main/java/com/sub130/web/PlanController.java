package com.sub130.web;

import com.sub130.dto.WorkoutDetail;
import com.sub130.plan.PlannedSession;
import com.sub130.service.PlanService;
import com.sub130.service.WorkoutService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;
    private final WorkoutService workoutService;

    public PlanController(PlanService planService, WorkoutService workoutService) {
        this.planService = planService;
        this.workoutService = workoutService;
    }

    /** Step-by-step breakdown of a day's session. */
    @GetMapping("/workout/{date}")
    public WorkoutDetail workout(@PathVariable String date) {
        return workoutService.expand(planService.forDate(date));
    }

    @GetMapping
    public List<PlannedSession> all() { return planService.all(); }

    @GetMapping("/week/{week}")
    public List<PlannedSession> week(@PathVariable int week) { return planService.week(week); }

    @GetMapping("/date/{date}")
    public PlannedSession forDate(@PathVariable String date) { return planService.forDate(date); }
}
