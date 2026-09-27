package com.sub130.strava;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strava")
public class StravaController {

    private final StravaService strava;
    private final StravaProperties props;

    public StravaController(StravaService strava, StravaProperties props) {
        this.strava = strava;
        this.props = props;
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return strava.status(); }

    /** Non-secret view of the stored API keys (client id + whether a secret is set). */
    @GetMapping("/config")
    public Map<String, Object> config() { return strava.configView(); }

    /** Save API keys entered in the UI. A blank secret keeps the stored one. */
    @PutMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody ConfigReq body) {
        strava.saveConfig(body.clientId(), body.clientSecret());
        return strava.configView();
    }

    public record ConfigReq(String clientId, String clientSecret) {}

    /** Kick off OAuth: browser hits this, we bounce to Strava's consent screen. */
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize() {
        if (!strava.isConfigured())
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Strava is not configured. Add your API keys in Settings."));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", strava.authorizeUrl()).build();
    }

    /** Strava redirects here with ?code=...; we exchange it and bounce back to the UI. */
    @GetMapping("/callback")
    public RedirectView callback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String error) {
        String base = props.getFrontendUrl() + "/settings";
        if (error != null || code == null) return new RedirectView(base + "?strava=denied");
        try {
            strava.exchangeCode(code);
            return new RedirectView(base + "?strava=connected");
        } catch (Exception e) {
            return new RedirectView(base + "?strava=error");
        }
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        List<StravaService.ImportedDay> days = strava.sync();
        return Map.of("imported", days.size(), "days", days);
    }

    /** Sync just one day's activity from Strava. */
    /** Called by the app on open: quietly import the last 3 days of runs (throttled). */
    @PostMapping("/sync-recent")
    public Map<String, Object> syncRecent() {
        if (!strava.isConnected()) return Map.of("status", "not-connected");
        try {
            var days = strava.syncRecent(3);
            if (days == null) return Map.of("status", "recently-synced");
            return Map.of("status", "synced", "imported", days.size(), "days", days);
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }

    @PostMapping("/sync/{date}")
    public ResponseEntity<?> syncDate(@PathVariable String date) {
        if (!strava.isConnected())
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Strava not connected"));
        List<StravaService.ImportedDay> days = strava.syncDate(date);
        return ResponseEntity.ok(Map.of("imported", days.size(), "days", days));
    }

    @PostMapping("/disconnect")
    public void disconnect() { strava.disconnect(); }

    /**
     * Import a raw Strava activities JSON array directly (no OAuth). Useful for a manual
     * export/paste, and it exercises the exact same mapping the live sync uses.
     */
    @PostMapping("/import")
    public Map<String, Object> importJson(@RequestBody JsonNode activities) {
        Iterable<JsonNode> it = activities.isArray() ? activities : List.of(activities);
        List<StravaService.ImportedDay> days = strava.importActivities(it);
        return Map.of("imported", days.size(), "days", days);
    }
}
