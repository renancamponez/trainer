package com.sub130.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub130.domain.DayLog;
import com.sub130.domain.WellnessConfig;
import com.sub130.repo.DayLogRepository;
import com.sub130.repo.WellnessConfigRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Morning readiness from Garmin, via intervals.icu: Garmin's official integration pushes overnight
 * HRV (rMSSD), resting HR and sleep score to intervals.icu within minutes of a watch sync, and this
 * reads them with a non-expiring API key. Called when the app opens; one request covers 5 weeks, so
 * missed days and the HRV baseline fill themselves in. Only the three readiness fields are written,
 * and a missing value never overwrites one that is already there.
 */
@Service
public class WellnessService {

    private static final ZoneId LOCAL = ZoneId.of("America/Denver");   // Fort Collins
    private static final int DAYS = 35;
    private static final Duration MIN_GAP = Duration.ofSeconds(45);    // page reloads don't hammer the API

    private final WellnessConfigRepository configRepo;
    private final DayLogRepository logRepo;
    private final MongoTemplate mongo;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private volatile Instant lastCall = Instant.EPOCH;

    public WellnessService(WellnessConfigRepository configRepo, DayLogRepository logRepo, MongoTemplate mongo,
                           @Value("${INTERVALS_BASE_URL:https://intervals.icu}") String baseUrl) {
        this.configRepo = configRepo;
        this.logRepo = logRepo;
        this.mongo = mongo;
        this.baseUrl = baseUrl;
    }

    private WellnessConfig cfg() { return configRepo.findById(WellnessConfig.ID).orElse(null); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    public boolean configured() { WellnessConfig c = cfg(); return c != null && !blank(c.athleteId) && !blank(c.apiKey); }

    /** Settings view: never returns the key itself. */
    public Map<String, Object> configView() {
        WellnessConfig c = cfg();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("athleteId", c == null || c.athleteId == null ? "" : c.athleteId);
        m.put("keySet", c != null && !blank(c.apiKey));
        Document last = mongo.findById("last", Document.class, "wellness_sync_status");
        if (last != null) { last.remove("_id"); m.put("lastRun", last); }
        return m;
    }

    /** Blank key keeps the stored one; a bare number becomes the "i123456" form intervals.icu uses. */
    public void saveConfig(String athleteId, String apiKey) {
        WellnessConfig c = configRepo.findById(WellnessConfig.ID).orElseGet(WellnessConfig::new);
        if (athleteId != null) {
            String a = athleteId.trim();
            c.athleteId = a.matches("\\d+") ? "i" + a : a;
        }
        if (!blank(apiKey)) c.apiKey = apiKey.trim();
        configRepo.save(c);
        lastCall = Instant.EPOCH;   // let the next sync run straight away
    }

    /**
     * Pull the last 5 weeks of wellness and merge it into the day logs.
     * status: synced | throttled | not-configured | error; todayHasData tells the page whether to keep waiting.
     */
    public synchronized Map<String, Object> sync(String todayIso, boolean force) {
        LocalDate today = todayIso != null ? LocalDate.parse(todayIso) : LocalDate.now(LOCAL);
        if (!configured()) return Map.of("status", "not-configured", "todayHasData", hasToday(today));
        Instant now = Instant.now();
        if (!force && now.isBefore(lastCall.plus(MIN_GAP)))
            return Map.of("status", "throttled", "todayHasData", hasToday(today));
        lastCall = now;

        WellnessConfig c = cfg();
        String url = baseUrl + "/api/v1/athlete/" + c.athleteId + "/wellness?oldest=" + today.minusDays(DAYS - 1)
                + "&newest=" + today;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            ("API_KEY:" + c.apiKey).getBytes(StandardCharsets.UTF_8)))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 || res.statusCode() == 403 || res.statusCode() == 404)
                return record(false, "intervals.icu rejected the athlete ID / API key (HTTP " + res.statusCode()
                        + ") - check them in Settings.", 0, today);
            if (res.statusCode() / 100 != 2)
                return record(false, "intervals.icu returned HTTP " + res.statusCode(), 0, today);

            int written = 0, withData = 0;
            for (JsonNode w : json.readTree(res.body())) {
                String date = w.path("id").asText("");
                if (date.length() != 10) continue;
                Double hrv = num(w, "hrv");
                Integer rhr = intOrNull(num(w, "restingHR"));
                Integer sleep = intOrNull(num(w, "sleepScore"));
                if (hrv == null && rhr == null && sleep == null) continue;
                withData++;
                DayLog log = logRepo.findById(date).orElseGet(() -> new DayLog(date));
                boolean changed = false;
                if (hrv != null && !Objects.equals(log.hrvMs, hrv)) { log.hrvMs = hrv; changed = true; }
                if (rhr != null && !Objects.equals(log.restingHr, rhr)) { log.restingHr = rhr; changed = true; }
                if (sleep != null && !Objects.equals(log.sleepScore, sleep)) { log.sleepScore = sleep; changed = true; }
                if (changed) { logRepo.save(log); written++; }
            }
            return record(true, withData + " day(s) with Garmin data, " + written + " updated", written, today);
        } catch (Exception e) {
            return record(false, "Couldn't reach intervals.icu: " + e.getClass().getSimpleName() + " "
                    + String.valueOf(e.getMessage()), 0, today);
        }
    }

    private boolean hasToday(LocalDate today) {
        return logRepo.findById(today.toString()).map(l -> l.hrvMs != null || l.sleepScore != null).orElse(false);
    }

    private Map<String, Object> record(boolean ok, String message, int written, LocalDate today) {
        Document d = new Document("_id", "last").append("ok", ok).append("message", message)
                .append("daysWritten", written).append("at", Instant.now().toString());
        mongo.save(d, "wellness_sync_status");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", ok ? "synced" : "error");
        m.put("message", message);
        m.put("todayHasData", hasToday(today));
        return m;
    }

    private static Double num(JsonNode w, String field) {
        JsonNode n = w.get(field);
        return n == null || n.isNull() || !n.isNumber() ? null : n.asDouble();
    }
    private static Integer intOrNull(Double d) { return d == null ? null : (int) Math.round(d); }
}
