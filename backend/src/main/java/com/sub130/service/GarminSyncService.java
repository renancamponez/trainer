package com.sub130.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Starts the Garmin readiness sync (the garmin-sync GitHub Actions workflow) on demand, so opening
 * the app in the morning pulls last night's HRV / sleep without waiting for the next scheduled run.
 * Needs GITHUB_TOKEN (fine-grained, Actions: read & write on the repo). Throttled so repeated page
 * loads start at most one run every few minutes.
 */
@Service
public class GarminSyncService {

    private static final Duration COOLDOWN = Duration.ofMinutes(4);

    private final String token;
    private final String repo;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile Instant lastDispatch = Instant.EPOCH;

    public GarminSyncService(@Value("${GITHUB_TOKEN:}") String token,
                             @Value("${GITHUB_REPO:renancamponez/trainer}") String repo) {
        this.token = token == null ? "" : token.trim();
        this.repo = repo;
    }

    public boolean configured() { return !token.isEmpty(); }

    /** status: started | recently-started | not-configured | error. */
    public synchronized Map<String, Object> trigger() {
        if (!configured()) return Map.of("status", "not-configured");
        Instant now = Instant.now();
        if (now.isBefore(lastDispatch.plus(COOLDOWN)))
            return Map.of("status", "recently-started", "startedAt", lastDispatch.toString());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://api.github.com/repos/" + repo + "/actions/workflows/garmin-sync.yml/dispatches"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"ref\":\"main\"}"))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204) {
                lastDispatch = now;
                return Map.of("status", "started", "startedAt", now.toString());
            }
            return Map.of("status", "error", "code", res.statusCode());
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }
}
