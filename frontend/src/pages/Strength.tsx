import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { StrengthProgram, StrengthExercise } from "../lib/types";
import ExerciseIcon from "../components/ExerciseIcon";

function ExerciseCard({ ex }: { ex: StrengthExercise }) {
  return (
    <div className="card" style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
      <div style={{ width: 92, height: 70, flexShrink: 0, background: "#0e141b", borderRadius: 8, padding: 6 }}>
        <ExerciseIcon name={ex.key} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div className="spread" style={{ gap: 8, alignItems: "baseline", flexWrap: "wrap" }}>
          <strong>{ex.name}</strong>
          <span className="pill" style={{ background: "#3b6ea533", color: "#7fb0e6" }}>{ex.dose}</span>
          {ex.legLift && <span className="pill" style={{ background: "#fc4c0222", color: "#fc7a44" }}>leg lift</span>}
        </div>
        <ul style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 13, color: "#93a1b0", lineHeight: 1.5 }}>
          {ex.cues.map((c, i) => <li key={i}>{c}</li>)}
        </ul>
      </div>
    </div>
  );
}

export default function Strength() {
  const [p, setP] = useState<StrengthProgram | null>(null);
  const [err, setErr] = useState(false);

  useEffect(() => { api.strength().then(setP).catch(() => setErr(true)); }, []);

  if (err) return <div className="loading">Couldn't load the strength program.</div>;
  if (!p) return <div className="loading">Loading…</div>;

  const gymColor = (t: string) => t === "full" ? "#4a9eff" : t === "upper" ? "#7fb0e6" : "#5c6b7a";

  return (
    <>
      <h1 className="page-title">Strength &amp; Posture</h1>
      <p className="page-sub">
        Pull-dominant gym work for the desk athlete, balanced around your runs so the key sessions stay sharp.
      </p>

      <div className="card" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>How it fits the running week</h3>
        <p className="note" style={{ marginTop: 0 }}>
          Leg lifting lands on the two quality-run days (Tue/Thu) so easy days stay easy; Friday drops the
          leg lifts to keep Saturday's long run fresh; Sunday is off. Every gym session is under an hour.
        </p>
        <table className="mtable">
          <thead><tr><th>Day</th><th>Run</th><th>Strength</th></tr></thead>
          <tbody>
            {p.week.map((s) => (
              <tr key={s.day}>
                <td className="k">{s.day}</td>
                <td>{s.runContext}</td>
                <td>
                  <span style={{ color: gymColor(s.gymType), fontWeight: 600 }}>{s.label}</span>
                  <div className="note" style={{ marginTop: 2 }}>{s.note}</div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="note" style={{ margin: "18px 0 6px", fontWeight: 700, color: "#7fb0e6" }}>
        THE DESK ROUTINE MATTERS MORE THAN THE GYM
      </div>
      <p className="note" style={{ marginTop: 0 }}>
        Three sessions a week won't out-run 45 hours of sitting. Stand every 30 minutes, keep the band on your
        desk, and get the monitor to eye level — that's the part that actually changes how you hold yourself.
      </p>

      <h2 className="page-title" style={{ fontSize: 20, marginTop: 22 }}>Daily desk routine</h2>
      <div className="grid cols-2" style={{ marginBottom: 12 }}>
        {p.desk.map((e) => <ExerciseCard key={e.key} ex={e} />)}
      </div>

      <h2 className="page-title" style={{ fontSize: 20, marginTop: 22 }}>The gym session</h2>
      <p className="page-sub">Three times a week in this order. Rest 60–90s between sets (2 min on the split squat and RDL).</p>
      <div className="grid cols-2" style={{ marginBottom: 12 }}>
        {p.gym.map((e) => <ExerciseCard key={e.key} ex={e} />)}
      </div>

      <h2 className="page-title" style={{ fontSize: 20, marginTop: 22 }}>Mobility finisher</h2>
      <p className="page-sub">Five minutes at the end of each gym session — breathe out slowly into each position.</p>
      <div className="grid cols-2" style={{ marginBottom: 12 }}>
        {p.mobility.map((e) => <ExerciseCard key={e.key} ex={e} />)}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h3 style={{ marginTop: 0 }}>What to expect</h3>
        <table className="mtable">
          <thead><tr><th>Week</th><th>What should be true</th></tr></thead>
          <tbody>
            {p.checklist.map((m) => (
              <tr key={m.weeks}><td className="k">{m.weeks}</td><td>{m.target}</td></tr>
            ))}
          </tbody>
        </table>
        <p className="note" style={{ marginBottom: 0 }}>
          General fitness information, not medical advice. Persistent neck, shoulder or lower-back pain is a
          physio's job, not a PDF's.
        </p>
      </div>
    </>
  );
}
