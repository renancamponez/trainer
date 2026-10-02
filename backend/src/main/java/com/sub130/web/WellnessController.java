package com.sub130.web;

import com.sub130.service.WellnessService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Garmin wellness (HRV, resting HR, sleep score) via intervals.icu. */
@RestController
@RequestMapping("/api/wellness")
public class WellnessController {

    private final WellnessService wellness;

    public WellnessController(WellnessService wellness) { this.wellness = wellness; }

    @GetMapping("/config")
    public Map<String, Object> config() { return wellness.configView(); }

    @PutMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody ConfigReq body) {
        wellness.saveConfig(body.athleteId(), body.apiKey());
        return wellness.configView();
    }
    public record ConfigReq(String athleteId, String apiKey) {}

    /** The app calls this on open; `today` is the athlete's local date. */
    @PostMapping("/sync")
    public Map<String, Object> sync(@RequestParam(required = false) String today,
                                    @RequestParam(defaultValue = "false") boolean force) {
        return wellness.sync(today, force);
    }
}
