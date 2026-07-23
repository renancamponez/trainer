package com.sub130.dto;

import java.util.List;

/**
 * Planned-vs-actual pace over the workout timeline, sampled on a common 10-second grid.
 * Pace values are seconds per km (lower = faster); null marks a gap (past a series' end,
 * or standing still). {@code hasActual} is false until the day has been synced from Strava.
 */
public record PaceProfile(String date, String title, boolean hasActual, List<Pt> points) {
    public record Pt(int t, Integer planned, Integer actual) {}
}
