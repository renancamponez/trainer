import type { DaySummary } from "./types";
import { fmtDur, paceFromKmMin } from "./format";

// Traffic-light colours (readable on the dark cards). Mirror the score thresholds so the
// per-metric colour and the session score always tell the same story.
const GREEN = "#43b968";
const YELLOW = "#e0a53a";
const RED = "#e05d5d";
const NEUTRAL = "#e6edf3";
const MUTED = "#6f7d8c";

export interface MetricRow {
  label: string;
  planned: string;
  plannedSub?: string;
  actual: string;
  color: string;
}

function rangeSec(range: string): [number, number] | null {
  const toks = range.match(/\d:\d{2}/g);
  if (!toks) return null;
  const secs = toks.map((t) => { const [m, s] = t.split(":"); return +m * 60 + +s; });
  return [Math.min(...secs), Math.max(...secs)];
}
function bpmBand(s: string | null): [number, number] | null {
  const m = (s ?? "").match(/(\d+)\D+(\d+)/);
  return m ? [+m[1], +m[2]] : null;
}
function ratioColor(r: number, greenLo: number, greenHi: number, yellowLo: number, yellowHi: number) {
  if (r >= greenLo && r <= greenHi) return GREEN;
  if (r >= yellowLo && r <= yellowHi) return YELLOW;
  return RED;
}

export function buildMetrics(s: DaySummary): MetricRow[] {
  const easy = s.type === "Easy" || s.type === "Long";
  const race = s.type === "RACE";
  const cleanPace = easy || race; // a single comparable target pace (workouts vary by rep)
  const rows: MetricRow[] = [];

  // Distance
  {
    let color = MUTED, actual = "—";
    if (s.actualKm != null && s.plannedKm) {
      actual = `${s.actualKm} km`;
      color = ratioColor(s.actualKm / s.plannedKm, 0.95, 1.15, 0.85, 1.3);
    }
    rows.push({ label: "Distance", planned: s.plannedKm != null ? `${s.plannedKm} km` : "—", actual, color });
  }

  // Pace (only where the target is a single range)
  if (cleanPace) {
    let color = NEUTRAL, actual = "—";
    if (s.actualKm && s.actualMinutes) {
      actual = paceFromKmMin(s.actualKm, s.actualMinutes);
      const band = rangeSec(s.paceRange);
      if (band) {
        const sec = (s.actualMinutes * 60) / s.actualKm;
        const [fast, slow] = band;
        color = sec >= fast && sec <= slow ? GREEN
          : sec > slow ? GREEN                 // easier than target is fine
          : fast - sec <= 10 ? YELLOW           // a touch quick
          : RED;                                // too fast
      }
    }
    rows.push({
      label: "Pace",
      planned: s.paceRange,
      plannedSub: easy ? `${s.mphRange} mph` : undefined,
      actual, color,
    });
  }

  // Duration
  {
    let color = MUTED, actual = "—";
    if (s.actualMinutes != null && s.estMinutes) {
      actual = fmtDur(s.actualMinutes);
      color = ratioColor(s.actualMinutes / s.estMinutes, 0.9, 1.2, 0.75, 1.35);
    }
    rows.push({ label: "Duration", planned: fmtDur(s.estMinutes), actual, color });
  }

  // Average HR
  {
    let color = NEUTRAL, actual = "—";
    const band = bpmBand(s.targetHrBpm);
    if (s.avgHr != null) {
      actual = `${s.avgHr} bpm`;
      if (band) {
        const [lo, hi] = band;
        color = easy
          ? (s.avgHr >= lo && s.avgHr <= hi ? GREEN : s.avgHr < lo ? GREEN : s.avgHr <= hi + 5 ? YELLOW : RED)
          : (s.avgHr >= lo ? GREEN : s.avgHr >= lo - 5 ? YELLOW : RED);
      }
    }
    rows.push({
      label: "Avg HR",
      planned: s.hrZone ? s.targetHrBpm!.replace(" bpm", "") : "n/a",
      plannedSub: s.hrZone ?? undefined,
      actual, color,
    });
  }

  return rows;
}
