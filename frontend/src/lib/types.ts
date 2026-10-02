export interface PlannedSession {
  week: number; date: string; dayName: string; phase: number; phaseName: string;
  type: string; session: string; warmup: string; cooldown: string;
  plannedKm: number | null; estMinutes: number; paceRange: string; mphRange: string;
  hrZone: string | null; targetHrBpm: string | null; incline: string; checkpoint: boolean;
}

export interface DaySummary {
  date: string; week: number; dayName: string; type: string; phaseName: string;
  session: string; plannedKm: number | null; estMinutes: number;
  paceRange: string; mphRange: string; hrZone: string | null; targetHrBpm: string | null;
  incline: string; checkpoint: boolean;
  done: boolean; source: string | null;
  actualKm: number | null; actualMinutes: number | null;
  avgHr: number | null; notes: string | null;
  hrvMs: number | null; restingHr: number | null; sleepScore: number | null;
  readinessScore: number | null;
  hrvPctOfBase: number | null; load: number | null; acwr: number | null;
  verdict: string | null; readinessAction: string | null;
  score: number | null; grade: string | null; scoreHeadline: string | null;
  // Set when that morning's readiness changed the session.
  adjustment: "TRIMMED" | "EASY" | "REST" | null; adjustmentNote: string | null;
  originalSession: string | null; originalKm: number | null;
}

export interface WeekSummary {
  week: number; phaseName: string; dates: string;
  plannedKm: number | null; actualKm: number | null;
  sessionsPlanned: number; sessionsDone: number;
  avgScore: number | null; checkpoint: string | null;
}

export interface DayLog {
  date: string; done: boolean;
  actualKm: number | null; actualMinutes: number | null;
  avgHr: number | null; notes: string | null;
  hrvMs: number | null; restingHr: number | null; sleepScore: number | null;
  source?: string | null;
}

export interface ScoreComponent { name: string; points: number; max: number; note: string; }
export interface ScoreResult {
  date: string; score: number | null; grade: string; headline: string;
  components: ScoreComponent[];
}

export interface Settings {
  id: string; age: number; maxHr: number; restingHr: number | null;
  lthr: number; lthrIsMeasured: boolean; goal: string;
}
export interface ZoneRow { zone: string; use: string; pctLo: number; pctHi: number; bpm: string; }
export interface SettingsResponse { settings: Settings; zones: ZoneRow[]; }

export interface StravaStatus {
  configured: boolean; connected: boolean; athlete?: string; lastSync?: string;
}
export interface StravaImportedDay { date: string; km: number; minutes: number; avgHr: number | null; }
export interface StravaSyncResult { imported: number; days: StravaImportedDay[]; }
export interface StravaConfigView { clientId: string; secretSet: boolean; source: "app" | "env" | "none"; }

export interface ReadinessResult {
  date: string; readinessScore: number | null;
  hrv: number | null; hrvBaseline: number | null; hrvPctOfBase: number | null;
  restingHr: number | null; rhrBaseline: number | null; sleepScore: number | null;
  load: number | null; acuteLoad: number | null; chronicLoad: number | null; acwr: number | null;
  verdict: string; action: string;
}

export interface WellnessRun { ok: boolean; message: string; daysWritten: number; at: string; }
export interface WellnessConfigView { athleteId: string; keySet: boolean; lastRun?: WellnessRun; }
export interface WellnessSyncResult {
  status: "synced" | "throttled" | "not-configured" | "error"; message?: string; todayHasData: boolean;
}

export interface StrengthExercise {
  key: string; name: string; dose: string; upperOnly: boolean; legLift: boolean; cues: string[];
}
export interface StrengthDaySlot {
  day: string; runContext: string; gymType: "full" | "upper" | "none"; label: string; note: string;
}
export interface StrengthMilestone { weeks: string; target: string; }
export interface StrengthProgram {
  week: StrengthDaySlot[]; gym: StrengthExercise[]; mobility: StrengthExercise[];
  desk: StrengthExercise[]; checklist: StrengthMilestone[];
}

export interface PaceProfilePoint { t: number; planned: number | null; actual: number | null; }
export interface PaceProfile {
  date: string; title: string; hasActual: boolean; points: PaceProfilePoint[];
}

export interface WorkoutStep {
  phase: string; what: string; amount: string; pace: string; mph: string; incline: string; hr: string; cue: string;
}
export interface WorkoutDetail {
  date: string; title: string; summary: string; steps: WorkoutStep[];
}

export interface GoalFactor { name: string; pct: number; detail: string; }
export interface GoalProjection {
  likelihood: number; band: string; headline: string;
  currentEquivalentHalf: string; goalHalf: string;
  elapsedWeeks: number; remainingWeeks: number;
  factors: GoalFactor[];
}
