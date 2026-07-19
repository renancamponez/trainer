package com.sub130.dto;

public record WeekSummary(
        int week, String phaseName, String dates,
        Double plannedKm, Double actualKm,
        int sessionsPlanned, int sessionsDone,
        Double avgScore, String checkpoint
) {}
