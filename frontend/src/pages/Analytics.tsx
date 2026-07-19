import { useEffect, useMemo, useState } from "react";
import {
  ResponsiveContainer, BarChart, Bar, LineChart, Line, XAxis, YAxis, Tooltip,
  CartesianGrid, ReferenceLine, Legend,
} from "recharts";
import { api } from "../lib/api";
import type { DaySummary, WeekSummary } from "../lib/types";

const AX = { stroke: "#93a1b0", fontSize: 11 };
const GRID = "#2c3742";

export default function Analytics() {
  const [days, setDays] = useState<DaySummary[]>([]);
  const [weeks, setWeeks] = useState<WeekSummary[]>([]);

  useEffect(() => {
    api.summary().then(setDays).catch(() => {});
    api.weekly().then(setWeeks).catch(() => {});
  }, []);

  const volume = useMemo(
    () => weeks.filter((w) => w.week >= 1).map((w) => ({
      week: w.week, planned: w.plannedKm ?? 0, actual: w.actualKm ?? 0,
    })), [weeks]);

  const readiness = useMemo(
    () => days.filter((d) => d.hrvPctOfBase != null).map((d) => ({ date: d.date.slice(5), hrv: d.hrvPctOfBase })),
    [days]);

  const acwr = useMemo(
    () => days.filter((d) => d.acwr != null).map((d) => ({ date: d.date.slice(5), acwr: d.acwr })),
    [days]);

  const scores = useMemo(
    () => days.filter((d) => d.score != null).map((d) => ({ date: d.date.slice(5), score: d.score })),
    [days]);

  const logged = days.filter((d) => d.done).length;

  return (
    <>
      <h1 className="page-title">Analytics</h1>
      <p className="page-sub">{logged} sessions logged so far.</p>

      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Weekly volume — planned vs actual (km)</h3>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={volume}>
            <CartesianGrid stroke={GRID} vertical={false} />
            <XAxis dataKey="week" tick={AX} interval={3} />
            <YAxis tick={AX} />
            <Tooltip contentStyle={{ background: "#1a2129", border: "1px solid #2c3742" }} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Bar dataKey="planned" fill="#2b5c94" name="Planned" />
            <Bar dataKey="actual" fill="#4a9eff" name="Actual" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="grid cols-2">
        <div className="card">
          <h3>HRV vs baseline (%)</h3>
          {readiness.length ? (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={readiness}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="date" tick={AX} />
                <YAxis tick={AX} domain={[80, 110]} />
                <Tooltip contentStyle={{ background: "#1a2129", border: "1px solid #2c3742" }} />
                <ReferenceLine y={95} stroke="#e8a13a" strokeDasharray="4 4" />
                <ReferenceLine y={90} stroke="#e03131" strokeDasharray="4 4" />
                <Line dataKey="hrv" stroke="#4a9eff" dot={{ r: 3 }} name="HRV %" />
              </LineChart>
            </ResponsiveContainer>
          ) : <div className="muted">Log HRV for ~7 days to see this.</div>}
        </div>

        <div className="card">
          <h3>Acute:Chronic workload ratio</h3>
          {acwr.length ? (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={acwr}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="date" tick={AX} />
                <YAxis tick={AX} domain={[0, 2]} />
                <Tooltip contentStyle={{ background: "#1a2129", border: "1px solid #2c3742" }} />
                <ReferenceLine y={1.5} stroke="#e03131" strokeDasharray="4 4" />
                <ReferenceLine y={0.8} stroke="#868e96" strokeDasharray="4 4" />
                <Line dataKey="acwr" stroke="#74b816" dot={{ r: 3 }} name="ACWR" />
              </LineChart>
            </ResponsiveContainer>
          ) : <div className="muted">Needs ~4 weeks of load data (0.8–1.3 is the safe band).</div>}
        </div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h3>Session scores</h3>
        {scores.length ? (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={scores}>
              <CartesianGrid stroke={GRID} vertical={false} />
              <XAxis dataKey="date" tick={AX} />
              <YAxis tick={AX} domain={[0, 10]} />
              <Tooltip contentStyle={{ background: "#1a2129", border: "1px solid #2c3742" }} />
              <ReferenceLine y={8} stroke="#2f9e44" strokeDasharray="4 4" />
              <Bar dataKey="score" fill="#4a9eff" name="Score /10" />
            </BarChart>
          </ResponsiveContainer>
        ) : <div className="muted">Score appears once you log sessions with average HR.</div>}
      </div>
    </>
  );
}
