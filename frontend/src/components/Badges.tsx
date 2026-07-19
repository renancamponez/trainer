import { verdictColor, typeColor, scoreColor } from "../lib/format";

export function VerdictBadge({ verdict }: { verdict: string | null }) {
  if (!verdict) return <span className="muted">—</span>;
  const label = verdict === "NEEDS_DATA" ? "NEEDS DATA" : verdict;
  return <span className="badge" style={{ background: verdictColor[verdict] || "#868e96" }}>{label}</span>;
}

export function TypeBadge({ type }: { type: string }) {
  return <span className="pill" style={{ background: (typeColor[type] || "#495057") + "33", color: typeColor[type] || "#adb5bd" }}>{type}</span>;
}

export function SourceBadge({ source }: { source: string | null | undefined }) {
  if (!source) return null;
  const strava = source === "strava";
  return (
    <span className="pill" title={strava ? "Synced from Strava" : "Entered by hand"}
      style={{ background: (strava ? "#fc4c02" : "#5c6b7a") + "33", color: strava ? "#fc4c02" : "#93a1b0" }}>
      {strava ? "⟳ Strava" : "✎ Manual"}
    </span>
  );
}

export function ScoreBadge({ score, grade }: { score: number | null; grade?: string | null }) {
  if (score == null) return <span className="muted">—</span>;
  return (
    <span className="badge" style={{ background: scoreColor(score) }}>
      {score.toFixed(1)}{grade ? ` · ${grade}` : ""}
    </span>
  );
}
