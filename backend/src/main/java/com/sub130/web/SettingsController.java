package com.sub130.web;

import com.sub130.domain.Settings;
import com.sub130.service.HrZoneService;
import com.sub130.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final HrZoneService hrZoneService;

    public SettingsController(SettingsService settingsService, HrZoneService hrZoneService) {
        this.settingsService = settingsService;
        this.hrZoneService = hrZoneService;
    }

    @GetMapping
    public Map<String, Object> get() {
        Settings s = settingsService.get();
        return Map.of("settings", s, "zones", hrZoneService.allZones(s.lthr));
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Settings body) {
        Settings s = settingsService.update(body);
        return Map.of("settings", s, "zones", hrZoneService.allZones(s.lthr));
    }

    @GetMapping("/zones")
    public List<HrZoneService.ZoneRow> zones() {
        return hrZoneService.allZones(settingsService.get().lthr);
    }
}
