package com.sub130.dto;

/** Computed readiness for one date. Composite of HRV, resting HR and sleep vs baseline. */
public record ReadinessResult(
        String date,
        Integer readinessScore, // composite 0-100 (null until at least one signal is available)
        Double hrv,
        Double hrvBaseline,     // rolling 7-day average of prior HRV
        Integer hrvPctOfBase,   // today vs baseline, %
        Integer restingHr,
        Double rhrBaseline,     // rolling 7-day average of prior resting HR
        Integer sleepScore,     // Garmin 0-100
        Double load,            // km-based session load
        Double acuteLoad,       // 7-day sum
        Double chronicLoad,     // 28-day average week
        Double acwr,            // acute : chronic
        String verdict,         // GREEN | AMBER | RED | NEEDS_DATA
        String action           // what to do today
) {}
