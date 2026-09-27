package com.sub130.strava;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub130.domain.DayLog;
import com.sub130.repo.DayLogRepository;
import com.sub130.service.RepDetector;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Strava integration: OAuth token exchange/refresh, activity fetch, and mapping
 * activities onto plan days. Fills the OBJECTIVE fields (km, minutes, avg HR) and
 * never touches HRV / sleep / notes.
 */
@Service
public class StravaService {

    private static final String AUTH = "https://www.strava.com/oauth/authorize";
    private static final String TOKEN = "https://www.strava.com/oauth/token";
    private static final String ACTIVITIES = "https://www.strava.com/api/v3/athlete/activities";
    private static final Set<String> RUN_TYPES = Set.of("Run", "TrailRun", "VirtualRun");
    private static final LocalDate PLAN_START = LocalDate.of(2026, 7, 17);
    private static final LocalDate PLAN_END = com.sub130.plan.PlanConstants.GOAL_RACE;

    private final StravaProperties props;
    private final StravaTokenRepository tokenRepo;
    private final StravaConfigRepository configRepo;
    private final DayLogRepository logRepo;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public StravaService(StravaProperties props, StravaTokenRepository tokenRepo,
                         StravaConfigRepository configRepo, DayLogRepository logRepo) {
        this.props = props;
        this.tokenRepo = tokenRepo;
        this.configRepo = configRepo;
        this.logRepo = logRepo;
    }

    public record ImportedDay(String date, double km, int minutes, Integer avgHr) {}

    // ---- credential resolution: stored config (from the UI) wins over the .env fallback ----
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private StravaConfig cfg() { return configRepo.findById(StravaConfig.ID).orElse(null); }

    private String clientId() {
        StravaConfig c = cfg();
        return (c != null && notBlank(c.clientId)) ? c.clientId : props.getClientId();
    }
    private String clientSecret() {
        StravaConfig c = cfg();
        return (c != null && notBlank(c.clientSecret)) ? c.clientSecret : props.getClientSecret();
    }
    public boolean isConfigured() { return notBlank(clientId()) && notBlank(clientSecret()); }

    /** Non-secret view for the UI: returns the client id and whether a secret is stored. */
    public Map<String, Object> configView() {
        StravaConfig c = cfg();
        String source = (c != null && notBlank(c.clientId)) ? "app"
                : (notBlank(props.getClientId()) ? "env" : "none");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientId", clientId() == null ? "" : clientId());
        m.put("secretSet", notBlank(clientSecret()));
        m.put("source", source);
        return m;
    }

    /** Save credentials from the UI. A blank secret leaves the stored one untouched. */
    public void saveConfig(String clientId, String clientSecret) {
        StravaConfig c = configRepo.findById(StravaConfig.ID).orElseGet(StravaConfig::new);
        c.clientId = clientId == null ? null : clientId.trim();
        if (notBlank(clientSecret)) c.clientSecret = clientSecret.trim();
        configRepo.save(c);
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", isConfigured());
        Optional<StravaToken> t = tokenRepo.findById(StravaToken.ID);
        m.put("connected", t.isPresent());
        t.ifPresent(tok -> {
            m.put("athlete", tok.athleteName);
            m.put("lastSync", tok.lastSync);
        });
        return m;
    }

    public String authorizeUrl() {
        return AUTH + "?client_id=" + clientId()
                + "&response_type=code"
                + "&redirect_uri=" + enc(props.getRedirectUri())
                + "&approval_prompt=auto"
                + "&scope=activity:read_all";
    }

    /** Exchange the OAuth code for tokens and persist them. */
    public void exchangeCode(String code) {
        JsonNode r = postForm(TOKEN, Map.of(
                "client_id", clientId(),
                "client_secret", clientSecret(),
                "code", code,
                "grant_type", "authorization_code"));
        StravaToken t = tokenRepo.findById(StravaToken.ID).orElseGet(StravaToken::new);
        t.accessToken = r.path("access_token").asText();
        t.refreshToken = r.path("refresh_token").asText();
        t.expiresAt = r.path("expires_at").asLong();
        JsonNode a = r.path("athlete");
        if (!a.isMissingNode()) {
            t.athleteId = a.path("id").asLong();
            t.athleteName = (a.path("firstname").asText("") + " " + a.path("lastname").asText("")).trim();
        }
        tokenRepo.save(t);
    }

    public void disconnect() { tokenRepo.deleteById(StravaToken.ID); }

    public boolean isConnected() { return tokenRepo.findById(StravaToken.ID).isPresent(); }

    /** Pull just one day's run(s) from Strava and import them onto that plan day. */
    public List<ImportedDay> syncDate(String date) {
        String token = validAccessToken();
        LocalDate d = LocalDate.parse(date);
        // Widen the window a day each side to cover timezone offsets, then filter exactly.
        long after = d.minusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
        long before = d.plusDays(2).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
        JsonNode arr = getJson(ACTIVITIES + "?after=" + after + "&before=" + before + "&per_page=50", token);
        List<JsonNode> onlyThisDay = new ArrayList<>();
        if (arr.isArray()) for (JsonNode a : arr) {
            String local = a.path("start_date_local").asText("");
            if (local.length() >= 10 && local.substring(0, 10).equals(date)) onlyThisDay.add(a);
        }
        List<ImportedDay> imported = importActivities(onlyThisDay, token, false); // explicit day: any date
        tokenRepo.findById(StravaToken.ID).ifPresent(t -> {
            t.lastSync = Instant.now().toString();
            tokenRepo.save(t);
        });
        return imported;
    }

    private String validAccessToken() {
        StravaToken t = tokenRepo.findById(StravaToken.ID)
                .orElseThrow(() -> new IllegalStateException("Strava not connected"));
        if (Instant.now().getEpochSecond() < t.expiresAt - 60) return t.accessToken;
        JsonNode r = postForm(TOKEN, Map.of(
                "client_id", clientId(),
                "client_secret", clientSecret(),
                "refresh_token", t.refreshToken,
                "grant_type", "refresh_token"));
        t.accessToken = r.path("access_token").asText();
        t.refreshToken = r.path("refresh_token").asText();
        t.expiresAt = r.path("expires_at").asLong();
        tokenRepo.save(t);
        return t.accessToken;
    }

    /** Pull activities from plan start and import runs. Returns the days written. */
    public List<ImportedDay> sync() {
        String token = validAccessToken();
        long after = PLAN_START.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() - 86400;
        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= 12; page++) {
            String url = ACTIVITIES + "?after=" + after + "&per_page=100&page=" + page;
            JsonNode arr = getJson(url, token);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(all::add);
            if (arr.size() < 100) break;
        }
        List<ImportedDay> imported = importActivities(all, token);
        StravaToken t = tokenRepo.findById(StravaToken.ID).orElseThrow();
        t.lastSync = Instant.now().toString();
        tokenRepo.save(t);
        return imported;
    }

    /** Map a list of Strava activity nodes onto plan days. Also used by the JSON-import test path. */
    public List<ImportedDay> importActivities(Iterable<JsonNode> activities) {
        return importActivities(activities, null);
    }

    /**
     * @param token if non-null, average HR is computed from the activity's HR stream with the
     *              first and last 2 minutes (warm-up / cool-down) trimmed off; otherwise it
     *              falls back to Strava's whole-activity average_heartrate.
     */
    public List<ImportedDay> importActivities(Iterable<JsonNode> activities, String token) {
        return importActivities(activities, token, true);
    }

    /**
     * @param enforcePlanRange when true (the bulk sync), only runs within the plan window are
     *        imported; when false (an explicit single-day sync) any date is allowed, so you can
     *        backfill runs that fall on rest days or before the plan even started.
     */
    public List<ImportedDay> importActivities(Iterable<JsonNode> activities, String token, boolean enforcePlanRange) {
        // Aggregate per date (a day can hold more than one run).
        Map<String, double[]> agg = new TreeMap<>(); // date -> [distM, movingS, hrTimeWeighted, hrTime]
        Map<String, List<DayLog.Rep>> repsByDate = new HashMap<>(); // reps from the day's primary (longest) run
        Map<String, List<Double>> speedByDate = new HashMap<>();
        Map<String, Double> primaryMovingS = new HashMap<>();
        for (JsonNode a : activities) {
            String type = a.hasNonNull("sport_type") ? a.get("sport_type").asText() : a.path("type").asText("");
            if (!RUN_TYPES.contains(type)) continue;
            String local = a.path("start_date_local").asText("");
            if (local.length() < 10) continue;
            LocalDate date = LocalDate.parse(local.substring(0, 10));
            if (enforcePlanRange && (date.isBefore(PLAN_START) || date.isAfter(PLAN_END))) continue;
            String key = date.toString();

            double[] v = agg.computeIfAbsent(key, k -> new double[4]);
            double distM = a.path("distance").asDouble(0);
            double movingS = a.path("moving_time").asDouble(0);
            v[0] += distM;
            v[1] += movingS;

            // One stream fetch per activity yields both the trimmed HR and the detected reps.
            StreamResult sr = (token != null && a.hasNonNull("id") && movingS >= 300)
                    ? analyzeStreams(a.get("id").asLong(), token) : null;

            Double hr = (sr != null && sr.trimmedHr != null) ? (double) sr.trimmedHr
                    : (a.hasNonNull("average_heartrate") ? a.get("average_heartrate").asDouble() : null);
            if (hr != null && movingS > 0) {
                v[2] += hr * movingS;
                v[3] += movingS;
            }
            if (sr != null && movingS > primaryMovingS.getOrDefault(key, 0.0)) {
                primaryMovingS.put(key, movingS);
                repsByDate.put(key, sr.reps);
                speedByDate.put(key, sr.speedSeries);
            }
        }

        List<ImportedDay> out = new ArrayList<>();
        for (var e : agg.entrySet()) {
            double[] v = e.getValue();
            DayLog log = logRepo.findById(e.getKey()).orElseGet(() -> new DayLog(e.getKey()));
            log.done = true;
            log.source = "strava";
            log.actualKm = Math.round(v[0] / 1000.0 * 100) / 100.0;
            log.actualMinutes = (int) Math.round(v[1] / 60.0);
            Integer hr = v[3] > 0 ? (int) Math.round(v[2] / v[3]) : null;
            if (hr != null) log.avgHr = hr;
            if (repsByDate.containsKey(e.getKey())) log.reps = repsByDate.get(e.getKey());
            if (speedByDate.containsKey(e.getKey())) log.speedSeries = speedByDate.get(e.getKey());
            logRepo.save(log);
            out.add(new ImportedDay(e.getKey(), log.actualKm, log.actualMinutes, log.avgHr));
        }
        return out;
    }

    private record StreamResult(Integer trimmedHr, List<DayLog.Rep> reps, List<Double> speedSeries) {}

    /**
     * One streams call (HR + velocity + distance) yielding the trimmed average HR (first/last
     * 2 min removed) and the hard efforts detected from the velocity stream. Either may be null/empty.
     */
    private StreamResult analyzeStreams(long activityId, String token) {
        try {
            JsonNode s = getJson("https://www.strava.com/api/v3/activities/" + activityId
                    + "/streams?keys=time,heartrate,velocity_smooth,distance&key_by_type=true", token);
            JsonNode tmN = s.path("time").path("data");
            JsonNode hrN = s.path("heartrate").path("data");
            JsonNode velN = s.path("velocity_smooth").path("data");
            JsonNode distN = s.path("distance").path("data");
            if (!tmN.isArray() || tmN.size() == 0) return new StreamResult(null, null, null);
            int n = tmN.size();
            int[] time = new int[n];
            for (int i = 0; i < n; i++) time[i] = tmN.get(i).asInt();

            Integer trimmed = null;
            int[] hr = null;
            if (hrN.isArray() && hrN.size() == n) {
                hr = new int[n];
                for (int i = 0; i < n; i++) hr[i] = hrN.get(i).asInt();
                int end = time[n - 1], lo = 120, hi = end - 120;
                if (hi > lo) {
                    long sum = 0; int cnt = 0;
                    for (int i = 0; i < n; i++) if (time[i] >= lo && time[i] <= hi && hr[i] > 0) { sum += hr[i]; cnt++; }
                    if (cnt > 0) trimmed = (int) Math.round((double) sum / cnt);
                }
            }

            List<DayLog.Rep> reps = null;
            List<Double> speedSeries = null;
            if (velN.isArray() && velN.size() == n) {
                double[] vel = new double[n];
                for (int i = 0; i < n; i++) vel[i] = velN.get(i).asDouble();
                double[] dist = null;
                if (distN.isArray() && distN.size() == n) {
                    dist = new double[n];
                    for (int i = 0; i < n; i++) dist[i] = distN.get(i).asDouble();
                }
                reps = RepDetector.detect(time, vel, hr, dist);
                speedSeries = downsampleSpeed(time, vel, 10);
            }
            return new StreamResult(trimmed, reps, speedSeries);
        } catch (Exception e) {
            return new StreamResult(null, null, null); // no stream, private activity, etc.
        }
    }

    /** Average speed (m/s) per {@code bucketS}-second bucket; index i => t = i*bucketS. */
    private static List<Double> downsampleSpeed(int[] time, double[] vel, int bucketS) {
        int end = time[time.length - 1];
        int buckets = end / bucketS + 1;
        double[] sum = new double[buckets];
        int[] cnt = new int[buckets];
        for (int i = 0; i < time.length; i++) {
            int b = time[i] / bucketS;
            if (b < buckets) { sum[b] += Math.max(0, vel[i]); cnt[b]++; }
        }
        List<Double> out = new ArrayList<>(buckets);
        for (int b = 0; b < buckets; b++)
            out.add(cnt[b] > 0 ? Math.round(sum[b] / cnt[b] * 100) / 100.0 : 0.0);
        return out;
    }

    // ---- tiny HTTP helpers ----
    private JsonNode getJson(String url, String bearer) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + bearer).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new RuntimeException("Strava GET " + res.statusCode() + ": " + res.body());
            return mapper.readTree(res.body());
        } catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new RuntimeException("Strava request failed", ex); }
    }

    private JsonNode postForm(String url, Map<String, String> form) {
        try {
            StringBuilder b = new StringBuilder();
            form.forEach((k, val) -> b.append(b.length() == 0 ? "" : "&").append(k).append("=").append(enc(val)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(b.toString())).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new RuntimeException("Strava token " + res.statusCode() + ": " + res.body());
            return mapper.readTree(res.body());
        } catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new RuntimeException("Strava token request failed", ex); }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
