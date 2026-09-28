import { Fragment, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import type { WeekSummary, PlannedSession } from "../lib/types";
import { fmtDate, fmtDur, TODAY } from "../lib/format";
import { TypeBadge } from "../components/Badges";

export default function Calendar() {
  const [weeks, setWeeks] = useState<WeekSummary[]>([]);
  const [open, setOpen] = useState<number | null>(null);
  const [days, setDays] = useState<Record<number, PlannedSession[]>>({});

  const currentRow = useRef<HTMLTableRowElement | null>(null);
  const isCurrent = (w: WeekSummary) => {
    const [start, end] = w.dates.split(" to ");
    return TODAY >= start && TODAY <= end;
  };

  // Open the current week and bring it into view on load.
  useEffect(() => {
    api.weekly().then((ws) => {
      setWeeks(ws);
      const cur = ws.find(isCurrent);
      if (cur) {
        toggle(cur.week);
        setTimeout(() => currentRow.current?.scrollIntoView({ block: "center", behavior: "smooth" }), 50);
      }
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
      <p className="page-sub">41 weeks + a 3-day intro, ending at the Colorado Half (2 May 2027). Click a week to see the sessions.</p>
      <div className="card" style={{ padding: 0 }}>
        <table>
          <thead>
            <tr><th>Wk</th><th>Phase</th><th>Planned</th><th>Done</th><th>Volume</th><th>Avg score</th><th>Checkpoint</th></tr>
          </thead>
          <tbody>
            {weeks.map((w) => {
              const cur = isCurrent(w);
              return (
              <Fragment key={w.week}>
                <tr onClick={() => toggle(w.week)} ref={cur ? currentRow : undefined}
                    style={{ cursor: "pointer", ...(cur ? { background: "#3b6ea52e", boxShadow: "inset 3px 0 0 #4a9eff" } : {}) }}>
                  <td style={{ whiteSpace: "nowrap" }}>
                    <b>{w.week}</b>
                    {cur && <span className="pill" style={{ marginLeft: 8, background: "#4a9eff33", color: "#7fb0e6" }}>This week</span>}
                  </td>
                  <td>{w.phaseName}</td>
                  <td>{w.sessionsPlanned}</td>
                  <td>{w.sessionsDone}</td>
                  <td>{w.actualKm ?? 0}/{w.plannedKm ?? 0} km</td>
                  <td>{w.avgScore != null ? w.avgScore.toFixed(1) : "—"}</td>
                  <td className="muted">{w.checkpoint ?? ""}</td>
                </tr>
                {open === w.week && (days[w.week] || []).map((d) => (
                  <tr key={d.date} style={{ background: "var(--panel-2)",
                        ...(d.date === TODAY ? { boxShadow: "inset 3px 0 0 #4a9eff" } : {}) }}>
                    <td>{d.date === TODAY && <span className="pill" style={{ background: "#4a9eff33", color: "#7fb0e6" }}>Today</span>}</td>
                    <td style={d.date === TODAY ? { fontWeight: 700 } : undefined}>{fmtDate(d.date)}</td>
                    <td colSpan={3}>
                      <TypeBadge type={d.type} /> <span style={{ marginLeft: 6 }}>{d.session.slice(0, 90)}</span>
                    </td>
                    <td>{d.plannedKm ? `${d.plannedKm} km · ${fmtDur(d.estMinutes)}` : "rest"}</td>
                    <td><Link to={`/log/${d.date}`} className="muted">log →</Link></td>
                  </tr>
                ))}
              </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}
