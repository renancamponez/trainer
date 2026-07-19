package com.sub130.service;

import com.sub130.plan.PlanConstants;
import org.springframework.stereotype.Service;

/** Turns an LTHR into concrete bpm ranges, exactly like the spreadsheet's zone formulas. */
@Service
public class HrZoneService {

    /** e.g. zone "Z2", lthr 172 -> "139-153 bpm". Returns null for a null zone. */
    public String bpmRange(String zone, int lthr) {
        double[] pct = PlanConstants.zonePct(zone);
        if (pct == null) return null;
        long lo = Math.round(lthr * pct[0]);
        long hi = Math.round(lthr * pct[1]);
        return lo + "-" + hi + " bpm";
    }

    public long[] bpmBounds(String zone, int lthr) {
        double[] pct = PlanConstants.zonePct(zone);
        if (pct == null) return null;
        return new long[]{ Math.round(lthr * pct[0]), Math.round(lthr * pct[1]) };
    }

    /** All five zones as display rows, for the Settings screen. */
    public java.util.List<ZoneRow> allZones(int lthr) {
        var rows = new java.util.ArrayList<ZoneRow>();
        for (int i = 0; i < PlanConstants.ZONE_NAMES.length; i++) {
            String z = PlanConstants.ZONE_NAMES[i];
            rows.add(new ZoneRow(z, PlanConstants.ZONE_USE[i],
                    (int) Math.round(PlanConstants.ZONE_PCT[i][0] * 100),
                    (int) Math.round(PlanConstants.ZONE_PCT[i][1] * 100),
                    bpmRange(z, lthr)));
        }
        return rows;
    }

    public record ZoneRow(String zone, String use, int pctLo, int pctHi, String bpm) {}
}
