import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "../lib/api";
import type { DaySummary, DayLog, ScoreResult, ReadinessResult } from "../lib/types";
import { fmtDate, paceFromKmMin } from "../lib/format";
import { TypeBadge, VerdictBadge, SourceBadge } from "../components/Badges";
import WorkoutSteps from "../components/WorkoutSteps";
import PaceChart from "../components/PaceChart";
import ReadinessBanner from "../components/ReadinessBanner";

const empty = (date: string): DayLog => ({
  date, done: false, actualKm: null, actualMinutes: null, avgHr: null,
  notes: null, hrvMs: null, restingHr: null, sleepScore: null,
});

export default function LogSession() {
  const { date = "" } = useParams();
  const [plan, setPlan] = useState<DaySummary | null>(null);
  const [log, setLog] = useState<DayLog>(empty(date));
  const [score, setScore] = useState<ScoreResult | null>(null);
  const [readiness, setReadiness] = useState<ReadinessResult | null>(null);
  const [savedCard, setSavedCard] = useState<"" | "session" | "readiness">("");

  useEffect(() => {
    api.daySummary(date).then(setPlan).catch(() => {});
    api.getLog(date).then((l) => setLog(l ?? empty(date)));
    api.score(date).then(setScore).catch(() => {});
    api.readiness(date).then(setReadiness).catch(() => {});
  }, [date]);

  function set<K extends keyof DayLog>(k: K, v: DayLog[K]) {
    setLog({ ...log, [k]: v });
    setSavedCard("");
  }
  const num = (s: string): number | null => (s === "" ? null : Number(s));

  // Both cards persist the same DayLog. The session card marks the day done; the
  // readiness card leaves "done" as-is. The backend decides source from km/min/HR.
  async function persist(done: boolean, which: "session" | "readiness") {
    const saved = await api.saveLog(date, { ...log, done });
    setLog(saved);
    setSavedCard(which);
    setScore(await api.score(date));
    api.daySummary(date).then(setPlan).catch(() => {});
    api.readiness(date).then(setReadiness).catch(() => {});
  }
  const saveSession = () => persist(true, "session");
  const saveReadiness = () => persist(log.done, "readiness");

  return (
    <>
      <p className="page-sub"><Link to="/calendar" className="muted">← Calendar</Link></p>
      <h1 className="page-title">{fmtDate(date)} {plan && <TypeBadge type={plan.type} />}</h1>
      {plan && plan.type !== "Off"
        ? <p className="page-sub">Week {plan.week} · {plan.session}</p>
        : <p className="page-sub">No scheduled training — log your readiness (and any run) below.</p>}

      <ReadinessBanner s={plan} />

      {plan && plan.type !== "Rest" && plan.type !== "Off" && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3>Workout — step by step</h3>
          <WorkoutSteps date={date} />
        </div>
      )}

      {plan && plan.type !== "Rest" && plan.type !== "Off" && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3 style={{ marginTop: 0 }}>Pace — planned vs actual</h3>
          <PaceChart date={date} />
        </div>
      )}

      <div className="grid cols-2">
        {/* --- Session actuals (can come from Strava) --- */}
        <div className="card">
          <div className="spread" style={{ marginBottom: 12 }}>
            <h3 style={{ margin: 0 }}>Log the session</h3>
            <SourceBadge source={log.source} />
          </div>
          {log.source === "strava" && (
            <div className="note" style={{ marginBottom: 10 }}>
              km / time / HR came from Strava. Editing any of these three marks the day manual —
              editing readiness below does not.
            </div>
          )}
          <div className="grid cols-2">
            <div className="field"><label>Actual km</label>
              <input type="number" step="0.1" value={log.actualKm ?? ""} onChange={(e) => set("actualKm", num(e.target.value))} /></div>
            <div className="field"><label>Actual minutes</label>
              <input type="number" value={log.actualMinutes ?? ""} onChange={(e) => set("actualMinutes", num(e.target.value))} /></div>
            <div className="field"><label>Avg HR (bpm)</label>
              <input type="number" value={log.avgHr ?? ""} onChange={(e) => set("avgHr", num(e.target.value))} /></div>
          </div>
          <div className="field"><label>Notes</label>
            <textarea rows={2} value={log.notes ?? ""} onChange={(e) => set("notes", e.target.value)} /></div>
          <div className="row">
            <button onClick={saveSession}>Save session</button>
            {savedCard === "session" && <span className="muted">Saved ✓</span>}
            {log.actualKm && log.actualMinutes ? <span className="muted">→ {paceFromKmMin(log.actualKm, log.actualMinutes)}</span> : null}
          </div>
        </div>

        {/* --- Morning readiness (always manual — never from Strava) --- */}
        <div className="card">
          <div className="spread" style={{ marginBottom: 12 }}>
            <h3 style={{ margin: 0 }}>Morning readiness</h3>
            <span className="pill" style={{ background: "#3b6ea533", color: "#7fb0e6" }}>Garmin · auto</span>
          </div>
          <div className="note" style={{ marginBottom: 12 }}>
            Synced from Garmin automatically each morning via intervals.icu (overnight HRV, resting HR, sleep score).
            Edit a value here if you need to — they drive your readiness score.
          </div>
          <div className="grid cols-3">
            <div className="field"><label>HRV (ms)</label>
              <input type="number" step="0.1" value={log.hrvMs ?? ""} onChange={(e) => set("hrvMs", num(e.target.value))} /></div>
            <div className="field"><label>Resting HR</label>
              <input type="number" value={log.restingHr ?? ""} onChange={(e) => set("restingHr", num(e.target.value))} /></div>
            <div className="field"><label>Sleep score (0–100)</label>
              <input type="number" min={0} max={100} value={log.sleepScore ?? ""} onChange={(e) => set("sleepScore", num(e.target.value))} /></div>
          </div>
          <div className="row">
            <button onClick={saveReadiness}>Save readiness</button>
            {savedCard === "readiness" && <span className="muted">Saved ✓</span>}
          </div>
        </div>
      </div>

      {/* --- Result --- */}
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Result</h3>
        {readiness && (
          <div style={{ marginBottom: 16 }}>
            <div className="row" style={{ gap: 12, alignItems: "baseline", marginBottom: 6 }}>
              <div className="muted" style={{ fontSize: 12 }}>Readiness</div>
              {readiness.readinessScore != null && <span style={{ fontSize: 24, fontWeight: 800 }}>{readiness.readinessScore}<span className="muted" style={{ fontSize: 13, fontWeight: 400 }}>/100</span></span>}
              <VerdictBadge verdict={readiness.verdict} />
            </div>
            <div className="muted" style={{ fontSize: 13, marginBottom: 10 }}>{readiness.action}</div>
            <table className="mtable"><tbody>
              <tr><td className="k">HRV</td><td>{readiness.hrv != null ? `${readiness.hrv} ms` : "—"}
                {readiness.hrvPctOfBase != null && <span className="muted"> · {readiness.hrvPctOfBase}% of {readiness.hrvBaseline}</span>}</td></tr>
              <tr><td className="k">Resting HR</td><td>{readiness.restingHr != null ? `${readiness.restingHr} bpm` : "—"}
                {readiness.restingHr != null && readiness.rhrBaseline != null && (() => { const dlt = Math.round(readiness.restingHr! - readiness.rhrBaseline!); return <span className="muted"> · usual {readiness.rhrBaseline}, {dlt >= 0 ? "+" : ""}{dlt}</span>; })()}</td></tr>
              <tr><td className="k">Sleep score</td><td>{readiness.sleepScore != null ? `${readiness.sleepScore}/100` : "—"}</td></tr>
              <tr><td className="k">ACWR</td><td>{readiness.acwr != null ? readiness.acwr : "—"}<span className="muted"> · load ratio (0.8–1.3 ideal)</span></td></tr>
            </tbody></table>
            <div className="note" style={{ marginTop: 6 }}>Composite: HRV 50% · resting HR 30% · sleep 20% (of whatever you've logged).</div>
          </div>
        )}
        {score && score.score != null ? (
          <>
            <div className="row" style={{ alignItems: "baseline", gap: 14 }}>
              <div className="big-score" style={{ color: "var(--accent)" }}>{score.score.toFixed(1)}</div>
              <div><div style={{ fontWeight: 700 }}>{score.grade}</div><div className="muted">{score.headline}</div></div>
            </div>
            <div className="grid cols-2" style={{ marginTop: 14 }}>
              {score.components.map((c) => (
                <div key={c.name} style={{ marginBottom: 6 }}>
                  <div className="spread"><span>{c.name}</span><span className="muted">{c.points.toFixed(1)}/{c.max}</span></div>
                  <div className="comp bar"><div style={{ width: `${(c.points / c.max) * 100}%` }} /></div>
                  <div className="note">{c.note}</div>
                </div>
              ))}
            </div>
          </>
        ) : <div className="muted">{score?.headline || "Save the session with your average HR to get a score."}</div>}
      </div>
    </>
  );
}
