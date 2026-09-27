package com.sub130.dto;

/** One merged row: plan + what was logged + readiness + score. The frontend's unit of display. */
public record DaySummary(
        String date, int week, String dayName, String type, String phaseName,
        String session, Double plannedKm, Integer estMinutes,
        String paceRange, String mphRange, String hrZone, String targetHrBpm, String incline,
        boolean checkpoint,
        boolean done, String source, Double actualKm, Integer actualMinutes, Integer avgHr, String notes,
        Double hrvMs, Integer restingHr, Integer sleepScore, Integer readinessScore,
        Integer hrvPctOfBase, Double load, Double acwr, String verdict, String readinessAction,
        Double score, String grade, String scoreHeadline,
        // Set when that morning's readiness changed the session (TRIMMED | EASY | REST).
        String adjustment, String adjustmentNote, String originalSession, Double originalKm
) {}
