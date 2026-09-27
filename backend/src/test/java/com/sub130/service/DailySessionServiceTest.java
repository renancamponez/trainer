package com.sub130.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Amber-day trimming: ~1/3 less work and 5 s/km easier, on the session texts the plan uses. */
class DailySessionServiceTest {

    @Test
    void manyRepsLoseAThird() {
        assertEquals("HILLS: 5 x 1min @ 5:33/km on 6% incline, 90s jog (1% incline)",
                DailySessionService.trim("HILLS: 8 x 1min @ 5:28/km on 6% incline, 90s jog (1% incline)"));
        assertEquals("VO2: 4 x 800m @ 4:47/km, 2min jog",
                DailySessionService.trim("VO2: 6 x 800m @ 4:42/km, 2min jog"));
    }

    @Test
    void fewRepsGetShorter() {
        assertEquals("THRESHOLD: 2 x 13min @ 4:37/km, 3min jog",
                DailySessionService.trim("THRESHOLD: 2 x 20min @ 4:32/km, 3min jog"));
        assertEquals("GOAL PACE: 3 x 1.5km @ 4:49/km, 2min jog",
                DailySessionService.trim("GOAL PACE: 3 x 2km @ 4:44/km, 2min jog"));
    }

    @Test
    void continuousEffortsAndSprints() {
        assertEquals("STEADY: 20min @ 5:14/km", DailySessionService.trim("STEADY: 30min @ 5:09/km"));
        assertEquals("7 x 20s hill sprints + 4 x 100m strides",
                DailySessionService.trim("10 x 20s hill sprints + 4 x 100m strides"));
    }

    @Test
    void unstructuredSessionsAreLeftAlone() {
        assertNull(DailySessionService.trim("Easy + 6 x 100m strides - down week"));
    }
}
