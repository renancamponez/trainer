package com.sub130.web;

import com.sub130.service.GarminSyncService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/garmin")
public class GarminController {

    private final GarminSyncService garmin;

    public GarminController(GarminSyncService garmin) { this.garmin = garmin; }

    @GetMapping("/status")
    public Map<String, Object> status() { return garmin.status(); }

    /** Start a Garmin readiness sync now (the app calls this when today's readiness is missing). */
    @PostMapping("/sync")
    public Map<String, Object> sync() { return garmin.trigger(); }
}
