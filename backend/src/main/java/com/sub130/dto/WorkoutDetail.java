package com.sub130.dto;

import java.util.List;

/** A workout broken into followable steps (warm-up, each rep, each recovery, cool-down). */
public record WorkoutDetail(String date, String title, String summary, List<Step> steps) {
    public record Step(
            String phase,     // Warm-up | Main set | Recovery | Strides | Cool-down | Run
            String what,      // e.g. "4 × threshold rep", "8 × hill sprint", "Easy run"
            String amount,    // duration and/or distance, e.g. "8 min (≈ 1.7 km)", "400 m (≈ 1:43)"
            String pace,      // min/km, or "—"
            String mph,       // treadmill speed, or "—"
            String incline,   // "1%", "6–8%"
            String hr,        // "Z4 · 162-170", or "—"
            String cue        // short coaching cue
    ) {}
}
