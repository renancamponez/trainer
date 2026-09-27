package com.sub130.dto;

import java.util.List;

/** Heuristic "goal outlook" — a coaching estimate, not a real probability. */
public record GoalProjection(
        int likelihood,               // 0-100
        String band,                  // ON_TRACK | HARD_BUT_LIVE | SLIPPING | OFF_TRACK
        String headline,
        String currentEquivalentHalf, // e.g. "1:44:30" — estimated fitness right now
        String goalHalf,              // e.g. "1:40:00"
        int elapsedWeeks,
        int remainingWeeks,
        List<Factor> factors
) {
    public record Factor(String name, int pct, String detail) {}
}
