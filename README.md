# Sub 1:30 — Half Marathon Tracker

A full-stack app built from the training-plan spreadsheet. It serves the same 52-week
plan, lets you log each session and your morning readiness inputs, and does the analysis
the spreadsheet did — HR zones, the readiness engine (HRV baseline / ACWR / GREEN-AMBER-RED),
and a 0–10 session score — with charts on top.

**Stack:** React + Vite + TypeScript · Spring Boot 3 (Java 17) · MongoDB. Everything runs
in Docker, so your local Java/Node versions don't matter.

## Run it

```bash
cd sub130-app
docker compose up --build
```

Then open **http://localhost:3000**. First launch takes a few minutes (Maven + npm build).

| Service  | URL                          | Notes                                  |
|----------|------------------------------|----------------------------------------|
| Frontend | http://localhost:3000        | nginx serving the built SPA            |
| Backend  | http://localhost:8080/api    | Spring Boot REST API                   |
| Mongo    | localhost:27017 (db `sub130`)| data persisted in the `mongo-data` volume |

The database seeds itself on first run with your real starting state (LTHR 172, and the
Fri 17 Jul run: 6 km, 36 min, HR 150) so the dashboard opens with a real score.

To stop: `docker compose down` (add `-v` to also wipe the database).

## What the backend actually computes

The valuable logic from the spreadsheet, ported to Java:

- **`plan/PlanGenerator`** — the deterministic 367-day plan (3-day intro + 52 weeks). All
  paces derive from `PlanConstants.PACE_SEC` (four numbers per phase), so a baseline change
  is a small edit. This port was verified **byte-for-byte** against the Python generator
  across all 367 days.
- **`service/HrZoneService`** — LTHR → bpm zones. Every HR figure keys off the single `lthr`
  value in Settings, exactly like the spreadsheet's `Settings!B7` cell.
- **`service/ReadinessService`** — rolling 7-day HRV baseline, km-based session load,
  acute:chronic workload ratio, and the GREEN/AMBER/RED verdict with an action for the day.
- **`service/ScoreService`** — the 0–10 session score across three axes (completion,
  HR-in-zone discipline, and easy-pace adherence). Components that lack data drop out and the
  rest rescale, so a sparsely logged day still scores fairly.

## Strava integration (optional)

Auto-fills **km, moving time, and average HR** on each session. It only writes those
objective fields — your manually entered **HRV, sleep score and notes are never overwritten**.

One-time setup (needs your own Strava account):

1. Go to **https://www.strava.com/settings/api** and create an API application.
   Set **Authorization Callback Domain** to `localhost`.
2. In the app, open **Settings → Strava**, paste in your **Client ID** and **Client Secret**,
   and click **Save keys**. (Keys are stored in the database; no restart needed. The secret is
   write-only — it's never sent back to the browser.)
3. Click **Connect with Strava**, authorize, then **Sync now**.

Prefer files? You can instead put the keys in `.env` (copy `.env.example`) and restart —
keys saved in the app override the `.env` fallback.

Sync pulls your runs from the plan start date, matches them to plan days by date, and
recomputes scores automatically. Tokens refresh themselves; runs of type Run/TrailRun/
VirtualRun are imported, everything else (rides, swims…) is ignored.

No Strava account or keys? You can still bulk-import: `POST /api/strava/import` with a raw
Strava activities JSON array runs the exact same mapping.

## API

```
GET  /api/plan                 full 367-day plan (HR bands resolved from current LTHR)
GET  /api/plan/week/{n}         one week
GET  /api/summary[?start&end]   merged plan + log + readiness + score per day
GET  /api/weekly                per-week rollup (planned vs actual, avg score)
GET  /api/readiness/{date}      readiness for one date
GET  /api/logs/{date}           a day's log
PUT  /api/logs/{date}           upsert a day's log
GET  /api/logs/{date}/score     score a logged session
GET  /api/settings              settings + computed HR zones
PUT  /api/settings              update settings (re-rates all zones)
GET  /api/strava/status         configured / connected / last sync
GET  /api/strava/authorize      start OAuth (redirects to Strava)
GET  /api/strava/callback       OAuth return (redirects back to the UI)
POST /api/strava/sync           pull recent runs into logs
POST /api/strava/import         import a raw activities JSON array (no OAuth)
POST /api/strava/disconnect     forget the stored tokens
```

## Screens

- **Today** — today's session, readiness verdict, and the live session score with its breakdown.
- **Calendar** — the 52-week table; expand any week to see and log its sessions.
- **Analytics** — weekly volume (planned vs actual), HRV-vs-baseline, ACWR, and score history.
- **Settings** — your numbers; changing LTHR re-rates every zone and score in the app.

## Local development (without Docker)

Needs Java 17+, Maven, Node 18+, and a local MongoDB.

```bash
# backend
cd backend && ./mvnw spring-boot:run        # or: mvn spring-boot:run

# frontend (proxies /api to :8080)
cd frontend && npm install && npm run dev    # http://localhost:5173
```

## Notes / honest limitations

- The score and readiness logic are only as good as their inputs. **LTHR is the anchor** —
  if it's an estimate, HR-in-zone scoring inherits that error. The Settings screen nags you
  to field-test it and tick "measured".
- HRV-guided readiness needs ~7 days of data before the baseline is meaningful, and ~4 weeks
  before ACWR is. Before then the app just says "train as planned".
- "Today" on the dashboard is the user's real local date. The plan itself is anchored to a
  fixed calendar (week 0 starts 17 Jul 2026), so real dates line up with plan days; if you're
  outside that window the cards say "No training scheduled" (still loggable/syncable).
