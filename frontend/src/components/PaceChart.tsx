import { useEffect, useState } from "react";
import {
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
} from "recharts";
import { api } from "../lib/api";
import type { PaceProfile } from "../lib/types";

const FLOOR = 180;   // 3:00/km — fastest shown
const CEIL = 540;    // 9:00/km — slower than this (walk recoveries) clamps to the floor line

const mmss = (sec: number | null) =>
  sec == null ? "—" : `${Math.floor(sec / 60)}:${String(Math.round(sec % 60)).padStart(2, "0")}`;
const clamp = (v: number | null) => (v == null ? null : Math.min(CEIL, Math.max(FLOOR, v)));

export default function PaceChart({ date }: { date: string }) {
  const [profile, setProfile] = useState<PaceProfile | null>(null);
  const [err, setErr] = useState(false);

  useEffect(() => {
    setProfile(null); setErr(false);
    api.profile(date).then(setProfile).catch(() => setErr(true));
  }, [date]);

  if (err) return <div className="muted">Couldn't load the pace profile.</div>;
  if (!profile) return <div className="muted">Loading pace profile…</div>;
  if (!profile.points.length) return <div className="muted">No pace profile for this day.</div>;

  const data = profile.points.map((p) => ({
    min: +(p.t / 60).toFixed(2),
    planned: clamp(p.planned),
    actual: clamp(p.actual),
  }));

  return (
    <div>
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#2c3742" />
          <XAxis
            dataKey="min" type="number" domain={[0, "dataMax"]}
            tick={{ fill: "#93a1b0", fontSize: 12 }} stroke="#3d4a57"
            tickFormatter={(m) => `${Math.round(m)}′`}
            label={{ value: "minutes", position: "insideBottom", offset: -2, fill: "#5c6b7a", fontSize: 11 }}
          />
          <YAxis
            reversed domain={[FLOOR, CEIL]} ticks={[180, 240, 300, 360, 420, 480, 540]}
            tick={{ fill: "#93a1b0", fontSize: 12 }} stroke="#3d4a57"
            tickFormatter={(s) => mmss(s)} width={44}
          />
          <Tooltip
            contentStyle={{ background: "#1a222c", border: "1px solid #35506e", borderRadius: 8 }}
            labelStyle={{ color: "#93a1b0" }}
            labelFormatter={(m) => `${Number(m).toFixed(1)} min`}
            formatter={(v: number, name: string) => [`${mmss(v)}/km`, name === "planned" ? "Planned" : "Actual"]}
          />
          <Legend formatter={(v) => (v === "planned" ? "Planned" : "Actual (Strava)")} />
          <Line
            type="stepAfter" dataKey="planned" stroke="#4a9eff" strokeWidth={2}
            strokeDasharray="6 4" dot={false} connectNulls isAnimationActive={false}
          />
          <Line
            type="monotone" dataKey="actual" stroke="#fc4c02" strokeWidth={2}
            dot={false} connectNulls={false} isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
      {!profile.hasActual && (
        <div className="note" style={{ marginTop: 6 }}>
          Showing the planned profile only. Sync this day from Strava to overlay your actual pace.
        </div>
      )}
      <div className="note" style={{ marginTop: 4 }}>
        Faster is higher. Recovery walks/jogs slower than 9:00/km sit on the bottom line.
      </div>
    </div>
  );
}
