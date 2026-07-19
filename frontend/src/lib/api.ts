import type {
  DaySummary, WeekSummary, DayLog, ScoreResult, SettingsResponse, Settings, PlannedSession,
  StravaStatus, StravaSyncResult, StravaConfigView, GoalProjection, WorkoutDetail,
} from "./types";

const BASE = import.meta.env.VITE_API_BASE || "/api";

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText} on ${path}`);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

export const api = {
  summary: (start?: string, end?: string) =>
    req<DaySummary[]>(`/summary${start ? `?start=${start}&end=${end}` : ""}`),
  daySummary: (date: string) => req<DaySummary>(`/summary/${date}`),
  weekly: () => req<WeekSummary[]>(`/weekly`),
  goal: (asOf: string) => req<GoalProjection>(`/goal?asOf=${asOf}`),
  planWeek: (week: number) => req<PlannedSession[]>(`/plan/week/${week}`),
  workout: (date: string) => req<WorkoutDetail>(`/plan/workout/${date}`),

  getLog: (date: string) => req<DayLog | null>(`/logs/${date}`).catch(() => null),
  saveLog: (date: string, log: Partial<DayLog>) =>
    req<DayLog>(`/logs/${date}`, { method: "PUT", body: JSON.stringify(log) }),
  score: (date: string) => req<ScoreResult>(`/logs/${date}/score`),

  settings: () => req<SettingsResponse>(`/settings`),
  saveSettings: (s: Settings) =>
    req<SettingsResponse>(`/settings`, { method: "PUT", body: JSON.stringify(s) }),

  stravaStatus: () => req<StravaStatus>(`/strava/status`),
  stravaSync: () => req<StravaSyncResult>(`/strava/sync`, { method: "POST" }),
  stravaSyncDate: (date: string) => req<StravaSyncResult>(`/strava/sync/${date}`, { method: "POST" }),
  stravaDisconnect: () => req<void>(`/strava/disconnect`, { method: "POST" }),
  stravaConfig: () => req<StravaConfigView>(`/strava/config`),
  stravaSaveConfig: (clientId: string, clientSecret: string) =>
    req<StravaConfigView>(`/strava/config`, { method: "PUT", body: JSON.stringify({ clientId, clientSecret }) }),
  // Connect is a full-page redirect (backend -> Strava), not a fetch:
  stravaConnectUrl: () => `${BASE}/strava/authorize`,
};
