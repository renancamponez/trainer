import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import type { DaySummary, ScoreResult, WeekSummary, GoalProjection, StrengthDaySlot } from "../lib/types";
import {
  TODAY, fmtDate, fmtDur, shiftDate, relativeLabel, bandColor, bandLabel, paceFromKmMin,
} from "../lib/format";
import { buildMetrics } from "../lib/metrics";
import { VerdictBadge, TypeBadge, ScoreBadge, SourceBadge } from "../components/Badges";
import GoalGauge from "../components/GoalGauge";
import WorkoutSteps from "../components/WorkoutSteps";
import ReadinessBanner from "../components/ReadinessBanner";

type Summaries = Record<string, DaySummary | null>;

function DayCard({
  date, summary, focus, stravaConnected, syncing, syncMsg, onSync, gym,
}: {
  date: string; summary: DaySummary | null | undefined; focus: boolean;
  stravaConnected: boolean; syncing: boolean; syncMsg?: string;
  onSync: (date: string) => void; gym?: StrengthDaySlot | null;
}) {
  const [showSteps, setShowSteps] = useState(false);
  const label = relativeLabel(date);
  // A real training session (rest / off-plan don't count).
  const planned = !!summary && summary.type !== "Rest" && summary.type !== "Off";
  const isScored = !!summary && summary.done && summary.score != null;
  const ranAnyway = !!summary && summary.done && summary.actualKm != null && !isScored; // e.g. run on a rest day
  const loggedReadiness = !!summary && summary.hrvMs != null;

  const band = isScored ? bandColor(summary!.score) : (date === TODAY ? "#4a9eff" : "#2c3742");
  const canSync = stravaConnected && date <= TODAY; // any day: rest, off-plan, or training

  return (
    <div className="card" style={{
      borderTop: `4px solid ${band}`,
      outline: focus ? "1px solid #35506e" : "none",
    }}>
      <div className="spread" style={{ marginBottom: 8 }}>
        <div className="row" style={{ gap: 8 }}>
          <strong>{label}</strong>
          {summary ? <TypeBadge type={summary.type} /> : <span className="pill" style={{ background: "#5c6b7a33", color: "#93a1b0" }}>Off</span>}
        </div>
        <span className="muted" style={{ fontSize: 11 }}>{fmtDate(date)}</span>
      </div>

      <ReadinessBanner s={summary} />

      {planned ? (
        <>
          <div className="muted" style={{ fontSize: 12.5, marginBottom: 10, lineHeight: 1.35 }}>{summary!.session}</div>
          <table className="mtable">
            <thead>
              <tr><th></th><th>Planned</th><th>Actual</th></tr>
            </thead>
            <tbody>
              {buildMetrics(summary!).map((m) => (
                <tr key={m.label}>
                  <td className="k">{m.label}</td>
                  <td className="p">
                    {m.planned}{m.plannedSub && <div className="sub">{m.plannedSub}</div>}
                  </td>
                  <td className="a" style={{ color: m.color }}>{m.actual}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <div style={{ marginBottom: 6 }}>
          <div className="muted" style={{ fontSize: 14 }}>
            {summary == null ? "No training scheduled." : summary.type === "Rest" ? summary.session : "No scheduled training."}
          </div>
          {ranAnyway && (
            <div style={{ fontSize: 13, marginTop: 8 }}>
              Logged run: {summary!.actualKm} km · {fmtDur(summary!.actualMinutes ?? undefined)} ·{" "}
              {paceFromKmMin(summary!.actualKm, summary!.actualMinutes)}{summary!.avgHr ? ` · ${summary!.avgHr} bpm` : ""}
            </div>
          )}
        </div>
      )}

      <div className="row" style={{ marginTop: 10, gap: 6, flexWrap: "wrap" }}>
        {isScored ? (
          <>
            <SourceBadge source={summary!.source} />
            <span className="pill" style={{ background: band + "33", color: band }}>● {bandLabel(summary!.score)}</span>
            <ScoreBadge score={summary!.score} grade={summary!.grade} />
          </>
        ) : ranAnyway ? (
          <SourceBadge source={summary!.source} />
        ) : planned ? (
          <span className="pill" style={{ background: "#3d4a5733", color: "#93a1b0" }}>not logged</span>
        ) : null}
        {loggedReadiness && summary!.verdict && summary!.verdict !== "NEEDS_DATA" && (
          <VerdictBadge verdict={summary!.verdict} />
        )}
        {loggedReadiness && (!summary!.verdict || summary!.verdict === "NEEDS_DATA") && (
          <span className="pill" style={{ background: "#3b6ea533", color: "#7fb0e6" }}>readiness logged</span>
        )}
      </div>

      {gym && gym.label !== "Off" && (() => {
        // The weekday schedule gives way to rest days and to readiness-adjusted days.
        const rest = summary?.type === "Rest";
        const eased = summary?.adjustment === "EASY" || summary?.adjustment === "REST";
        const noGym = rest || eased;
        const label = rest ? "Rest day - desk routine only"
                    : eased ? "Upper body / desk routine only (readiness)" : gym.label;
        const light = noGym || gym.gymType === "none";
        return (
          <div className="note" style={{ marginTop: 8, color: light ? "#93a1b0" : "#7fb0e6" }}>
            {light ? "🧍" : "🏋"} {label}
          </div>
        );
      })()}

      <div className="row" style={{ marginTop: 12, gap: 8, flexWrap: "wrap" }}>
        <Link to={`/log/${date}`}><button className="ghost">{summary?.done || loggedReadiness ? "Edit" : "Log"}</button></Link>
        {planned && (
          <button className="ghost" onClick={() => setShowSteps((v) => !v)}>
            {showSteps ? "Hide steps" : "Step-by-step"}
          </button>
        )}
        {canSync && (
          <button style={{ background: "#fc4c02" }} onClick={() => onSync(date)} disabled={syncing}>
            {syncing ? "Syncing…" : "Sync Strava"}
          </button>
        )}
      </div>
      {showSteps && planned && <div style={{ marginTop: 10 }}><WorkoutSteps date={date} /></div>}
      {syncMsg && <div className="note" style={{ marginTop: 6 }}>{syncMsg}</div>}
    </div>
  );
}

export default function Dashboard() {
  const [summaries, setSummaries] = useState<Summaries>({});
  const [today, setToday] = useState<DaySummary | null>(null);
  const [weeks, setWeeks] = useState<WeekSummary[]>([]);
  const [goal, setGoal] = useState<GoalProjection | null>(null);
  const [connected, setConnected] = useState(false);
  const [gymWeek, setGymWeek] = useState<StrengthDaySlot[] | null>(null);
  const [anchor, setAnchor] = useState(TODAY);
  const [focusScore, setFocusScore] = useState<ScoreResult | null>(null);
  const [syncingDate, setSyncingDate] = useState<string | null>(null);
  const [syncMsg, setSyncMsg] = useState<Record<string, string>>({});
  const [err, setErr] = useState<string | null>(null);
  // Morning readiness from Garmin: "waiting" while a sync runs, "late" if Garmin has nothing yet.
  const [garmin, setGarmin] = useState<"idle" | "waiting" | "late" | "failed" | "setup" | "done">("idle");
  const [garminError, setGarminError] = useState<string>("");

  const dates = useMemo(() => [shiftDate(anchor, -1), anchor, shiftDate(anchor, 1)], [anchor]);

  useEffect(() => {
    api.daySummary(TODAY).then(setToday).catch((e) => setErr(String(e)));
    api.weekly().then(setWeeks).catch(() => {});
    api.goal(TODAY).then(setGoal).catch(() => {});
    api.stravaStatus().then((s) => setConnected(s.connected)).catch(() => {});
    api.strength().then((s) => setGymWeek(s.week)).catch(() => {});
    // Auto-sync: pull the last few days of runs from Strava, then refresh what they change.
    api.stravaSyncRecent().then((r) => {
      if (r.status !== "synced" || !r.imported) return;
      api.daySummary(TODAY).then(setToday).catch(() => {});
      [shiftDate(TODAY, -1), TODAY, shiftDate(TODAY, 1)].forEach(loadDate);
      api.weekly().then(setWeeks).catch(() => {});
      api.goal(TODAY).then(setGoal).catch(() => {});
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // If this morning's HRV / sleep haven't arrived, pull them (Garmin -> intervals.icu -> here, ~1 s).
  // If Garmin hasn't sent last night yet (watch not synced), check again every minute for 30 min.
  // The loop lives in refs so other refreshes of `today` (e.g. the Strava auto-sync) can't cancel it.
  const garminStarted = useRef(false);
  const garminTimer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => clearTimeout(garminTimer.current), []);   // stop only when leaving the page
  useEffect(() => {
    if (!today || garminStarted.current || today.hrvMs != null || today.sleepScore != null) return;
    garminStarted.current = true;
    setGarmin("waiting");
    const started = Date.now();
    let failures = 0;
    const attempt = async () => {
      try {
        const r = await api.wellnessSync(TODAY);
        failures = 0;
        if (r.todayHasData) {
          const s = await api.daySummary(TODAY);
          setToday(s);
          setSummaries((p) => ({ ...p, [TODAY]: s }));
          api.goal(TODAY).then(setGoal).catch(() => {});
          setGarmin("done");
          return;
        }
        if (r.status === "not-configured") { setGarmin("setup"); return; }
        if (r.status === "error") { setGarminError(r.message ?? "unknown error"); setGarmin("failed"); return; }
        setGarmin("late");   // synced fine, but Garmin has nothing for last night yet
      } catch (e) {
        // One blip is fine; two in a row means something is actually wrong - say so instead of spinning.
        if (++failures >= 2) { setGarminError(`Couldn't reach the app server: ${String(e)}`); setGarmin("failed"); }
      }
      if (Date.now() - started < 30 * 60_000) garminTimer.current = setTimeout(attempt, 60_000);
    };
    attempt();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [today]);

  // Mon..Sun schedule indexed by weekday (JS getDay: 0=Sun..6=Sat).
  const gymFor = (d: string): StrengthDaySlot | null =>
    gymWeek ? gymWeek[(new Date(d + "T00:00:00").getDay() + 6) % 7] : null;

  // Fetch each visible date individually so off-plan days (not in the bulk plan) load too.
  function loadDate(d: string) {
    api.daySummary(d).then((s) => setSummaries((p) => ({ ...p, [d]: s }))).catch(() => {});
  }
  useEffect(() => { dates.forEach(loadDate); /* eslint-disable-next-line */ }, [dates.join(",")]);

  const focus = summaries[anchor];
  useEffect(() => {
    if (focus && focus.done && focus.type !== "Rest" && focus.type !== "Off") {
      api.score(anchor).then(setFocusScore).catch(() => setFocusScore(null));
    } else setFocusScore(null);
  }, [anchor, focus?.done]);

  async function syncDay(date: string) {
    setSyncingDate(date);
    try {
      const res = await api.stravaSyncDate(date);
      loadDate(date);
      api.goal(TODAY).then(setGoal).catch(() => {});   // sync changes actuals -> refresh the outlook
      setSyncMsg((p) => ({
        ...p,
        [date]: res.imported > 0 ? `Imported ${res.days[0].km} km from Strava` : "No Strava run found for this day",
      }));
      if (date === anchor) api.score(anchor).then(setFocusScore).catch(() => {});
    } catch {
      setSyncMsg((p) => ({ ...p, [date]: "Sync failed — is Strava connected?" }));
    } finally {
      setSyncingDate(null);
    }
  }

  if (err) return <div className="loading">Backend not reachable: {err}</div>;

  const done = weeks.reduce((a, w) => a + w.sessionsDone, 0);
  const planned = weeks.reduce((a, w) => a + w.sessionsPlanned, 0);
  const goalWeek = weeks.length ? weeks[weeks.length - 1] : undefined;   // goal race = last plan week
  const rangeLabel = `${fmtDate(dates[0])} – ${fmtDate(dates[2])}`;

  return (
    <>
      <h1 className="page-title">Home</h1>
      <p className="page-sub">
        {focus && focus.type !== "Off" ? `Week ${focus.week} · ${focus.phaseName} phase` : "Outside the plan window"}
      </p>

      {garmin === "waiting" && (
        <div style={{ background: "#3b6ea522", borderLeft: "3px solid #4a9eff", borderRadius: 6,
                      padding: "10px 12px", marginBottom: 14, fontSize: 13.5 }}>
          <span className="pulse-dot" />
          Syncing last night's HRV, resting HR and sleep from Garmin… today's session updates on its own.
        </div>
      )}
      {garmin === "failed" && (
        <div style={{ background: "#e5484d22", borderLeft: "3px solid #f2777b", borderRadius: 6,
                      padding: "10px 12px", marginBottom: 14, fontSize: 13.5, lineHeight: 1.45 }}>
          <b style={{ color: "#f2777b" }}>The Garmin sync is failing.</b>{" "}
          {/rejected|API key|athlete/i.test(garminError)
            ? <>Check the intervals.icu athlete ID and API key on the <Link to="/settings">Settings</Link> page.</>
            : <>Today's session is shown as planned; this page will try again on your next visit.</>}
          <div className="muted" style={{ fontSize: 11.5, marginTop: 4 }}>{garminError}</div>
        </div>
      )}
      {garmin === "late" && (
        <div style={{ background: "#e0a10022", borderLeft: "3px solid #f0b73a", borderRadius: 6,
                      padding: "10px 12px", marginBottom: 14, fontSize: 13.5 }}>
          Garmin hasn't sent last night's data yet. Open Garmin Connect on your phone to sync your watch —
          this page checks every minute and updates today's session when it arrives.
        </div>
      )}
      {garmin === "setup" && (
        <div style={{ background: "#3b6ea522", borderLeft: "3px solid #4a9eff", borderRadius: 6,
                      padding: "10px 12px", marginBottom: 14, fontSize: 13.5 }}>
          Connect your Garmin readiness (HRV, resting HR, sleep) once on the{" "}
          <Link to="/settings">Settings</Link> page — about 5 minutes via intervals.icu, then it's automatic.
        </div>
      )}
      {goal && <div style={{ marginBottom: 16 }}><GoalGauge goal={goal} /></div>}

      <div className="grid cols-3" style={{ marginBottom: 20 }}>
        <div className="tile">
          <div className="label">Readiness (today)</div>
          {garmin === "waiting" || garmin === "late" || garmin === "failed" || garmin === "setup" ? (
            <>
              <div className="value" style={{ fontSize: 17, display: "flex", alignItems: "center" }}>
                {(garmin === "waiting" || garmin === "late") && <span className="pulse-dot" />}
                {garmin === "waiting" ? "Syncing from Garmin…" : garmin === "failed" ? "Garmin sync failed"
                  : garmin === "setup" ? "Not connected" : "Waiting for Garmin"}
              </div>
              <div className="sub">
                {garmin === "waiting" ? "Last night's HRV, resting HR and sleep"
                  : garmin === "failed" ? "See the message above - training shown as planned"
                  : garmin === "setup" ? "Connect Garmin readiness in Settings"
                  : "Sync your watch in Garmin Connect — checking every minute"}
              </div>
            </>
          ) : (
            <>
              <div className="value" style={{ display: "flex", gap: 8, alignItems: "baseline" }}>
                {today?.readinessScore != null && <span>{today.readinessScore}</span>}
                <VerdictBadge verdict={today?.verdict ?? null} />
              </div>
              <div className="sub">{today?.readinessAction ?? "—"}</div>
            </>
          )}
        </div>
        <div className="tile">
          <div className="label">Plan progress</div>
          <div className="value">{done}/{planned}</div>
          <div className="sub">sessions completed</div>
        </div>
        <div className="tile">
          <div className="label">Goal race</div>
          <div className="value">1:40</div>
          <div className="sub">Colorado Half · {goalWeek ? goalWeek.dates.split(" to ")[1] : "2027-05-02"} · stretch sub-1:30</div>
        </div>
      </div>

      <div className="spread" style={{ marginBottom: 12 }}>
        <div className="row" style={{ gap: 8 }}>
          <button className="ghost" onClick={() => setAnchor(shiftDate(anchor, -1))}>◀ Prev</button>
          <button className="ghost" onClick={() => setAnchor(TODAY)} disabled={anchor === TODAY}>Today</button>
          <button className="ghost" onClick={() => setAnchor(shiftDate(anchor, 1))}>Next ▶</button>
        </div>
        <span className="muted" style={{ fontSize: 13 }}>{rangeLabel}</span>
      </div>

      <div className="grid cols-3">
        {dates.map((d) => (
          <DayCard
            key={d}
            date={d}
            summary={summaries[d]}
            focus={d === anchor}
            stravaConnected={connected}
            syncing={syncingDate === d}
            syncMsg={syncMsg[d]}
            onSync={syncDay}
            gym={gymFor(d)}
          />
        ))}
      </div>

      {focus && focus.type !== "Rest" && focus.type !== "Off" && (
        <div className="card" style={{ marginTop: 16 }}>
          <h3>{relativeLabel(anchor)}'s score</h3>
          {focusScore && focusScore.score != null ? (
            <>
              <div className="row" style={{ alignItems: "baseline", gap: 14, marginBottom: 12 }}>
                <div className="big-score" style={{ color: bandColor(focusScore.score) }}>{focusScore.score.toFixed(1)}</div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 18 }}>{focusScore.grade}</div>
                  <div className="muted" style={{ fontSize: 13 }}>{focusScore.headline}</div>
                </div>
              </div>
              <div className="grid cols-2">
                {focusScore.components.map((c) => (
                  <div key={c.name} style={{ marginBottom: 6 }}>
                    <div className="spread"><span>{c.name}</span><span className="muted">{c.points.toFixed(1)}/{c.max}</span></div>
                    <div className="comp bar"><div style={{ width: `${(c.points / c.max) * 100}%`, background: bandColor(focusScore.score) }} /></div>
                    <div className="note">{c.note}</div>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="muted">Not logged yet — log the session or sync it from Strava to get a score.</div>
          )}
        </div>
      )}
    </>
  );
}
