import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { WorkoutDetail } from "../lib/types";

const phaseColor: Record<string, string> = {
  "Warm-up": "#3b6ea5", "Main set": "#c98a1a", Recovery: "#5c6b7a",
  Strides: "#7048b6", "Cool-down": "#3b6ea5", Race: "#c0392b", "Pick-up": "#2f9e44", Run: "#2f9e44",
};

export default function WorkoutSteps({ date }: { date: string }) {
  const [detail, setDetail] = useState<WorkoutDetail | null>(null);
  useEffect(() => { api.workout(date).then(setDetail).catch(() => setDetail(null)); }, [date]);

  if (!detail || detail.steps.length === 0) return null;

  return (
    <div style={{ overflowX: "auto" }}>
      <table className="mtable steps">
        <thead>
          <tr>
            <th>Phase</th><th>What</th><th>Time / distance</th>
            <th>Pace</th><th>Speed</th><th>Incline</th><th>HR</th>
          </tr>
        </thead>
        <tbody>
          {detail.steps.map((s, i) => (
            <tr key={i}>
              <td><span className="pill" style={{ background: (phaseColor[s.phase] || "#5c6b7a") + "33", color: phaseColor[s.phase] || "#adb5bd" }}>{s.phase}</span></td>
              <td>
                {s.what}
                <div className="sub" style={{ maxWidth: 260, whiteSpace: "normal" }}>{s.cue}</div>
              </td>
              <td style={{ whiteSpace: "nowrap" }}>{s.amount}</td>
              <td style={{ whiteSpace: "nowrap" }}>{s.pace}</td>
              <td style={{ whiteSpace: "nowrap" }}>{s.mph}</td>
              <td>{s.incline}</td>
              <td className="sub">{s.hr}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
