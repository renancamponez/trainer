package com.sub130.dto;

/** Computed readiness for one date, mirroring the spreadsheet's Readiness row. */
public record ReadinessResult(
        String date,
        Double hrv,
        Double hrvBaseline,     // rolling 7-day average of the prior 7 days
        Integer hrvPctOfBase,   // today vs baseline, %
        Double load,            // km-based session load
        Double acuteLoad,       // sum of last 7 days
        Double chronicLoad,     // 28-day average week
        Double acwr,            // acute : chronic
        String verdict,         // GREEN | AMBER | RED | NEEDS_DATA
        String action           // what to do today
) {}
