import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { Settings, ZoneRow } from "../lib/types";
import StravaCard from "../components/StravaCard";
import WellnessCard from "../components/WellnessCard";

export default function SettingsPage() {
  const [s, setS] = useState<Settings | null>(null);
  const [zones, setZones] = useState<ZoneRow[]>([]);
  const [saved, setSaved] = useState(false);

  useEffect(() => { api.settings().then((r) => { setS(r.settings); setZones(r.zones); }).catch(() => {}); }, []);
  if (!s) return <div className="loading">Loading…</div>;

  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => { setS({ ...s, [k]: v }); setSaved(false); };
  const num = (x: string): number => (x === "" ? 0 : Number(x));

  async function save() {
    const r = await api.saveSettings(s!);
    setS(r.settings); setZones(r.zones); setSaved(true);
  }

  return (
    <>
      <h1 className="page-title">Settings</h1>
      <p className="page-sub">Every HR zone keys off LTHR — change it and the whole app re-rates.</p>

      <div className="grid cols-2">
        <div className="card">
          <h3>Your numbers</h3>
          <div className="grid cols-2">
            <div className="field"><label>Age</label>
              <input type="number" value={s.age} onChange={(e) => set("age", num(e.target.value))} /></div>
            <div className="field"><label>Max HR (bpm)</label>
              <input type="number" value={s.maxHr} onChange={(e) => set("maxHr", num(e.target.value))} /></div>
            <div className="field"><label>Resting HR (bpm)</label>
              <input type="number" value={s.restingHr ?? ""} onChange={(e) => set("restingHr", num(e.target.value))} /></div>
            <div className="field">
              <label>LTHR — threshold HR (bpm)</label>
              <input type="number" value={s.lthr} onChange={(e) => set("lthr", num(e.target.value))} />
            </div>
          </div>
          <div className="field">
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" style={{ width: "auto" }} checked={s.lthrIsMeasured}
                onChange={(e) => set("lthrIsMeasured", e.target.checked)} />
              LTHR is field-tested (not an estimate)
            </label>
          </div>
          {!s.lthrIsMeasured && (
            <div className="note" style={{ marginBottom: 12 }}>
              ⚠️ LTHR is still an estimate. Run the 30-min field test (avg HR over the final 20 min)
              and tick the box — every zone and score depends on this number.
            </div>
          )}
          <div className="row"><button onClick={save}>Save</button>{saved && <span className="muted">Saved ✓</span>}</div>
        </div>

        <div className="card">
          <h3>HR zones (from LTHR {s.lthr})</h3>
          <table>
            <thead><tr><th>Zone</th><th>Used for</th><th>% LTHR</th><th>Target bpm</th></tr></thead>
            <tbody>
              {zones.map((z) => (
                <tr key={z.zone}>
                  <td><b>{z.zone}</b></td><td>{z.use}</td>
                  <td className="muted">{z.pctLo}–{z.pctHi}%</td><td><b>{z.bpm}</b></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div style={{ marginTop: 16, maxWidth: 520 }}>
        <StravaCard />
        <div style={{ marginTop: 16 }}><WellnessCard /></div>
      </div>
    </>
  );
}
