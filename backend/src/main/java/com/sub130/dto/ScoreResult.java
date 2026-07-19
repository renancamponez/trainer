package com.sub130.dto;

import java.util.List;

/** A 0-10 quality score for one completed session, with an explainable breakdown. */
public record ScoreResult(
        String date,
        Double score,          // 0-10, null if nothing to score
        String grade,          // A+, A, B, ... or "-"
        String headline,
        List<Component> components
) {
    public record Component(String name, double points, double max, String note) {}
}
