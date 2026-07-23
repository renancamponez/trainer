package com.sub130.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub130.domain.DayLog.Rep;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the velocity-based rep detector against a real Strava treadmill run:
 * 2026-07-23 was an easy 6 km + 6 x 100m strides session. The detector must recover
 * the six strides from the velocity stream (no laps were recorded).
 */
class RepDetectorTest {

    @Test
    void findsTheSixStridesInARealTreadmillRun() throws Exception {
        JsonNode s;
        try (InputStream in = getClass().getResourceAsStream("/streams_2026-07-23.json")) {
            assertNotNull(in, "fixture stream missing");
            s = new ObjectMapper().readTree(in);
        }
        int n = s.get("time").size();
        int[] time = new int[n];
        double[] vel = new double[n];
        int[] hr = new int[n];
        double[] dist = new double[n];
        for (int i = 0; i < n; i++) {
            time[i] = s.get("time").get(i).asInt();
            vel[i] = s.get("vel").get(i).asDouble();
            hr[i] = s.get("hr").get(i).asInt();
            dist[i] = s.get("dist").get(i).asDouble();
        }

        List<Rep> reps = RepDetector.detect(time, vel, hr, dist);

        assertEquals(6, reps.size(), "should detect all six strides");
        for (Rep r : reps) {
            assertTrue(r.durationS >= 8 && r.durationS <= 40, "stride duration plausible: " + r.durationS);
            assertTrue(r.paceSec > 220 && r.paceSec < 320, "stride pace ~4:xx/km: " + r.paceSec);
            assertNotNull(r.avgHr);
            assertTrue(r.avgHr > 155, "stride HR clearly elevated: " + r.avgHr);
        }
    }
}
