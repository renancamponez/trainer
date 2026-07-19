import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { StravaStatus, StravaSyncResult, StravaConfigView } from "../lib/types";

const ORANGE = "#fc4c02";

export default function StravaCard() {
  const [status, setStatus] = useState<StravaStatus | null>(null);
  const [config, setConfig] = useState<StravaConfigView | null>(null);
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [savingKeys, setSavingKeys] = useState(false);
  const [keysMsg, setKeysMsg] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [result, setResult] = useState<StravaSyncResult | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function refresh() {
    const [s, c] = await Promise.all([api.stravaStatus(), api.stravaConfig()]);
    setStatus(s); setConfig(c); setClientId(c.clientId || "");
  }
  useEffect(() => { refresh().catch(() => {}); }, []);

  const flag = new URLSearchParams(window.location.search).get("strava");

  async function saveKeys() {
    setSavingKeys(true); setKeysMsg(null); setErr(null);
    try {
      await api.stravaSaveConfig(clientId.trim(), clientSecret.trim());
      setClientSecret("");
      await refresh();
      setKeysMsg("Keys saved.");
    } catch (e) { setErr(String(e)); }
    finally { setSavingKeys(false); }
  }
  async function sync() {
    setSyncing(true); setErr(null);
    try { setResult(await api.stravaSync()); await refresh(); }
    catch (e) { setErr(String(e)); }
    finally { setSyncing(false); }
  }
  async function disconnect() { await api.stravaDisconnect(); await refresh(); setResult(null); }

  if (!status || !config) return null;

  const pill = status.connected ? "connected" : status.configured ? "not connected" : "needs API keys";
  const pillBg = status.connected ? ORANGE : status.configured ? "#3d4a57" : "#7a5901";

  return (
    <div className="card" style={{ borderTop: `4px solid ${ORANGE}` }}>
      <div className="spread" style={{ marginBottom: 10 }}>
        <h3 style={{ margin: 0 }}>Strava</h3>
        <span className="pill" style={{ background: pillBg, color: "#fff" }}>{pill}</span>
      </div>

      {flag === "connected" && <div className="note" style={{ color: "#2f9e44" }}>✓ Strava connected. Hit “Sync now”.</div>}
      {flag === "denied" && <div className="note" style={{ color: "#e8a13a" }}>Authorization was declined.</div>}
      {flag === "error" && <div className="note" style={{ color: "#e03131" }}>Something went wrong connecting.</div>}

      {!status.connected && (
        <>
          <div className="muted" style={{ fontSize: 13, marginBottom: 12 }}>
            Create an API app at{" "}
            <a href="https://www.strava.com/settings/api" target="_blank" rel="noreferrer" style={{ color: ORANGE }}>
              strava.com/settings/api
            </a>{" "}
            (Authorization Callback Domain: <code>localhost</code>), then paste the keys here.
            Sync fills km, time and HR — never your HRV, sleep or notes.
          </div>
          <div className="field">
            <label>Client ID</label>
            <input value={clientId} onChange={(e) => setClientId(e.target.value)} placeholder="e.g. 12345" />
          </div>
          <div className="field">
            <label>Client Secret</label>
            <input type="password" value={clientSecret} onChange={(e) => setClientSecret(e.target.value)}
              placeholder={config.secretSet ? "•••••••• saved — leave blank to keep" : "paste secret"} />
          </div>
          <div className="row">
            <button onClick={saveKeys} disabled={savingKeys || !clientId.trim()}>
              {savingKeys ? "Saving…" : "Save keys"}
            </button>
            {status.configured && (
              <a href={api.stravaConnectUrl()}>
                <button style={{ background: ORANGE }}>Connect with Strava</button>
              </a>
            )}
            {keysMsg && <span className="muted">{keysMsg}</span>}
          </div>
          {config.source === "env" && (
            <div className="note">Currently using keys from <code>.env</code>. Saving here overrides them.</div>
          )}
        </>
      )}

      {status.connected && (
        <>
          <table>
            <tbody>
              <tr><td className="muted">Athlete</td><td>{status.athlete || "—"}</td></tr>
              <tr><td className="muted">Last sync</td><td>{status.lastSync ? new Date(status.lastSync).toLocaleString() : "never"}</td></tr>
            </tbody>
          </table>
          <div className="row" style={{ marginTop: 14 }}>
            <button style={{ background: ORANGE }} onClick={sync} disabled={syncing}>
              {syncing ? "Syncing…" : "Sync now"}
            </button>
            <button className="ghost" onClick={disconnect}>Disconnect</button>
          </div>
          {result && (
            <div className="note" style={{ marginTop: 10 }}>
              Imported {result.imported} day{result.imported === 1 ? "" : "s"}
              {result.days.length > 0 && `: ${result.days.map((d) => `${d.date} (${d.km}km)`).join(", ")}`}
            </div>
          )}
        </>
      )}
      {err && <div className="note" style={{ color: "#e03131" }}>{err}</div>}
    </div>
  );
}
