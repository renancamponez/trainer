import type { DaySummary } from "../lib/types";

const STYLE: Record<string, { bg: string; fg: string; label: string }> = {
  TRIMMED: { bg: "#e0a10022", fg: "#f0b73a", label: "Trimmed for readiness" },
  EASY: { bg: "#e5484d22", fg: "#f2777b", label: "Swapped to easy - readiness low" },
  REST: { bg: "#e5484d22", fg: "#f2777b", label: "Rest day - readiness very low" },
  NOTE: { bg: "#3b6ea522", fg: "#7fb0e6", label: "Readiness note" },
};

/** Shows how (and why) this morning's HRV / resting HR / sleep changed the planned session. */
export default function ReadinessBanner({ s }: { s: DaySummary | null | undefined }) {
  if (!s || !s.adjustmentNote) return null;
  const st = STYLE[s.adjustment ?? "NOTE"] ?? STYLE.NOTE;
  return (
    <div style={{ background: st.bg, borderLeft: `3px solid ${st.fg}`, borderRadius: 6,
                  padding: "8px 10px", margin: "0 0 10px", fontSize: 12.5, lineHeight: 1.4 }}>
      <div style={{ color: st.fg, fontWeight: 700, marginBottom: 2 }}>
        {st.label}{s.readinessScore != null ? ` · ${s.readinessScore}/100` : ""}
      </div>
      <div style={{ color: "#c9d3dd" }}>{s.adjustmentNote}</div>
      {s.originalSession && (
        <div className="muted" style={{ marginTop: 4, fontSize: 11.5 }}>
          Planned: <span style={{ textDecoration: "line-through" }}>{s.originalSession}</span>
          {s.originalKm != null ? ` (${s.originalKm} km)` : ""}
        </div>
      )}
    </div>
  );
}
