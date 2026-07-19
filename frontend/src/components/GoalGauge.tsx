import type { GoalProjection } from "../lib/types";

const BAND: Record<string, { color: string; label: string }> = {
  ON_TRACK: { color: "#43b968", label: "On track" },
  HARD_BUT_LIVE: { color: "#e0a53a", label: "Hard but live" },
  SLIPPING: { color: "#e07a3a", label: "Slipping" },
  OFF_TRACK: { color: "#e05d5d", label: "Off track" },
};

function factorColor(pct: number) {
  return pct >= 70 ? "#43b968" : pct >= 40 ? "#e0a53a" : "#e05d5d";
}

export default function GoalGauge({ goal }: { goal: GoalProjection }) {
  const band = BAND[goal.band] ?? BAND.HARD_BUT_LIVE;
  const r = 90, cx = 110, cy = 110;
  const len = Math.PI * r;
  const frac = Math.max(0, Math.min(1, goal.likelihood / 100));
  const arc = `M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`;

  return (
    <div className="card">
      <div className="spread" style={{ marginBottom: 4 }}>
        <h3 style={{ margin: 0 }}>Goal outlook</h3>
        <span className="muted" style={{ fontSize: 11 }}>heuristic · not a guarantee</span>
      </div>

      <div className="goal-grid">
        <div style={{ textAlign: "center" }}>
          <svg viewBox="0 0 220 132" width="100%" style={{ maxWidth: 240 }}>
            <path d={arc} fill="none" stroke="#2c3742" strokeWidth={16} strokeLinecap="round" />
            <path d={arc} fill="none" stroke={band.color} strokeWidth={16} strokeLinecap="round"
              strokeDasharray={`${frac * len} ${len}`} />
            <text x={cx} y={cy - 18} textAnchor="middle" fontSize="40" fontWeight="800" fill={band.color}>
              {goal.likelihood}%
            </text>
            <text x={cx} y={cy + 6} textAnchor="middle" fontSize="13" fill="#93a1b0">
              {band.label}
            </text>
          </svg>
          <div style={{ fontSize: 13, marginTop: 2 }}>
            Fitness now ≈ <b>{goal.currentEquivalentHalf}</b>
            <span className="muted"> · goal {goal.goalHalf}</span>
          </div>
          <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>
            Week {goal.elapsedWeeks} · {goal.remainingWeeks} to go
          </div>
        </div>

        <div>
          <div style={{ fontSize: 14, marginBottom: 12 }}>{goal.headline}</div>
          {goal.factors.map((f) => (
            <div key={f.name} style={{ marginBottom: 9 }}>
              <div className="spread" style={{ fontSize: 12.5 }}>
                <span>{f.name}</span>
                <span className="muted">{f.detail}</span>
              </div>
              <div className="gbar"><div style={{ width: `${Math.min(100, f.pct)}%`, background: factorColor(f.pct) }} /></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
