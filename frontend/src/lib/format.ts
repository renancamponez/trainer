// The user's real local date (not UTC — build it from local components so evening
// timezones don't roll over to tomorrow). Computed once at load.
function localToday(): string {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}
export const TODAY = localToday();

export function fmtDur(min: number | null | undefined): string {
  if (!min || min <= 0) return "—";
  const h = Math.floor(min / 60), m = min % 60;
  return h > 0 ? `${h}h ${String(m).padStart(2, "0")}m` : `${m} min`;
}

export function fmtDate(iso: string): string {
  const d = new Date(iso + "T00:00:00");
  return d.toLocaleDateString("en-GB", { weekday: "short", day: "2-digit", month: "short" });
}

export function paceFromKmMin(km: number | null, min: number | null): string {
  if (!km || !min || km <= 0) return "—";
  const secPerKm = (min * 60) / km;
  const m = Math.floor(secPerKm / 60);
  const s = Math.round(secPerKm % 60);
  return `${m}:${String(s).padStart(2, "0")}/km`;
}

export const verdictColor: Record<string, string> = {
  GREEN: "#2f9e44", AMBER: "#e8a13a", RED: "#e03131", NEEDS_DATA: "#868e96",
};

export const typeColor: Record<string, string> = {
  Rest: "#495057", Off: "#5c6b7a", Easy: "#3b6ea5", Long: "#2f9e44", Workout: "#c98a1a", RACE: "#c0392b",
};

export function scoreColor(score: number | null): string {
  if (score == null) return "#868e96";
  if (score >= 9) return "#2f9e44";
  if (score >= 7.5) return "#74b816";
  if (score >= 6) return "#e8a13a";
  return "#e03131";
}

// Three-band traffic light for a completed session's score.
export function bandColor(score: number | null): string {
  if (score == null) return "#3d4a57"; // not done yet — neutral
  if (score >= 8) return "#2f9e44";     // green
  if (score >= 6) return "#e8a13a";     // yellow
  return "#e03131";                     // red
}

export function bandLabel(score: number | null): string {
  if (score == null) return "";
  if (score >= 8) return "on track";
  if (score >= 6) return "okay";
  return "off target";
}

export function shiftDate(iso: string, days: number): string {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function nextDay(iso: string): string {
  return shiftDate(iso, 1);
}

// "Today" / "Yesterday" / "Tomorrow" relative to the plan clock, else the date.
export function relativeLabel(iso: string): string {
  if (iso === TODAY) return "Today";
  if (iso === shiftDate(TODAY, -1)) return "Yesterday";
  if (iso === shiftDate(TODAY, 1)) return "Tomorrow";
  return fmtDate(iso);
}
