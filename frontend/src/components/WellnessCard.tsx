import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { WellnessConfigView } from "../lib/types";
import { TODAY } from "../lib/format";

const BLUE = "#4a9eff";

/** Garmin wellness via intervals.icu: one-time athlete ID + API key, then fully automatic. */
export default function WellnessCard() {
  const [cfg, setCfg] = useState<WellnessConfigView | null>(null);
  const [athleteId, setAthleteId] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function refresh() {
    const c = await api.wellnessConfig();
    setCfg(c); setAthleteId(c.athleteId || "");
  }
  useEffect(() => { refresh().catch(() => {}); }, []);

  async function saveAndTest() {
    setBusy(true); setMsg(null);
    try {
      await api.wellnessSaveConfig(athleteId.trim(), apiKey.trim());
      setApiKey("");
      const r = await api.wellnessSync(TODAY, true);
      setMsg(r.status === "synced" ? `✓ Connected - ${r.message}.` : `✗ ${r.message ?? r.status}`);
      await refresh();
    } catch (e) { setMsg(`✗ ${String(e)}`); }
    finally { setBusy(false); }
  }

  if (!cfg) return null;
  const connected = cfg.keySet && !!cfg.athleteId;
  const last = cfg.lastRun;
  const pill = !connected ? "not set up" : last && !last.ok ? "failing" : "connected";
  const pillBg = !connected ? "#7a5901" : last && !last.ok ? "#c92a2a" : "#2f9e44";

  return (
    <div className="card" style={{ borderTop: `4px solid ${BLUE}` }}>
      <div className="spread" style={{ marginBottom: 10 }}>
        <h3 style={{ margin: 0 }}>Garmin readiness (via intervals.icu)</h3>
        <span className="pill" style={{ background: pillBg, color: "#fff" }}>{pill}</span>
      </div>
      <div className="note" style={{ marginBottom: 10, lineHeight: 1.5 }}>
        Overnight HRV, resting HR and sleep score reach the app automatically: Garmin sends them to{" "}
        <a href="https://intervals.icu" target="_blank" rel="noreferrer">intervals.icu</a> (official integration) and
        the app reads them when you open it. One-time setup: free intervals.icu account → Settings → Connections →
        Garmin, tick <i>Download wellness data</i> → Settings → Developer Settings → copy your athlete ID and API key here.
      </div>
      <div className="grid cols-2">
        <div className="field"><label>Athlete ID</label>
          <input value={athleteId} placeholder="i123456" onChange={(e) => setAthleteId(e.target.value)} /></div>
        <div className="field"><label>API key {cfg.keySet && <span className="muted">(saved - leave blank to keep)</span>}</label>
          <input type="password" value={apiKey} placeholder={cfg.keySet ? "••••••••" : "paste API key"}
                 onChange={(e) => setApiKey(e.target.value)} /></div>
      </div>
      <div className="row" style={{ gap: 10, alignItems: "center" }}>
        <button onClick={saveAndTest} disabled={busy || !athleteId.trim() || (!apiKey.trim() && !cfg.keySet)}>
          {busy ? "Testing…" : "Save & test"}
        </button>
        {msg && <span className="note">{msg}</span>}
      </div>
      {last && (
        <div className="note" style={{ marginTop: 8 }}>
          Last sync {new Date(last.at).toLocaleString()}: {last.ok ? "" : "failed - "}{last.message}
        </div>
      )}
    </div>
  );
}
