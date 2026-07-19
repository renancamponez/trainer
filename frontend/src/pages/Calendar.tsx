import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import type { WeekSummary, PlannedSession } from "../lib/types";
import { fmtDate, fmtDur } from "../lib/format";
import { TypeBadge } from "../components/Badges";

export default function Calendar() {
  const [weeks, setWeeks] = useState<WeekSummary[]>([]);
  const [open, setOpen] = useState<number | null>(null);
  const [days, setDays] = useState<Record<number, PlannedSession[]>>({});

  useEffect(() => { api.weekly().then(setWeeks).catch(() => {}); }, []);

  async function toggle(week: number) {
    if (open === week) { setOpen(null); return; }
    setOpen(week);
    if (!days[week]) {
      const d = await api.planWeek(week);
      setDays((prev) => ({ ...prev, [week]: d }));
    }
  }

  return (
    <>
      <h1 className="page-title">Calendar</h1>
      <p className="page-sub">52 weeks + a 3-day intro. Click a week to see the sessions.</p>
      <div className="card" style={{ padding: 0 }}>
        <table>
          <thead>
            <tr><th>Wk</th><th>Phase</th><th>Planned</th><th>Done</th><th>Volume</th><th>Avg score</th><th>Checkpoint</th></tr>
          </thead>
          <tbody>
            {weeks.map((w) => (
              <Fragment key={w.week}>
                <tr onClick={() => toggle(w.week)} style={{ cursor: "pointer" }}>
                  <td><b>{w.week}</b></td>
                  <td>{w.phaseName}</td>
                  <td>{w.sessionsPlanned}</td>
                  <td>{w.sessionsDone}</td>
                  <td>{w.actualKm ?? 0}/{w.plannedKm ?? 0} km</td>
                  <td>{w.avgScore != null ? w.avgScore.toFixed(1) : "—"}</td>
                  <td className="muted">{w.checkpoint ?? ""}</td>
                </tr>
                {open === w.week && (days[w.week] || []).map((d) => (
                  <tr key={d.date} style={{ background: "var(--panel-2)" }}>
                    <td></td>
                    <td>{fmtDate(d.date)}</td>
                    <td colSpan={3}>
                      <TypeBadge type={d.type} /> <span style={{ marginLeft: 6 }}>{d.session.slice(0, 90)}</span>
                    </td>
                    <td>{d.plannedKm ? `${d.plannedKm} km · ${fmtDur(d.estMinutes)}` : "rest"}</td>
                    <td><Link to={`/log/${d.date}`} className="muted">log →</Link></td>
                  </tr>
                ))}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
