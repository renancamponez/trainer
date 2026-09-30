#!/usr/bin/env python3
"""
Pull morning readiness from Garmin Connect into the sub130 database:
overnight HRV (last-night average, ms), resting HR and sleep score (0-100).

Runs on GitHub Actions a few times a day (.github/workflows/garmin-sync.yml) and
re-reads the last 7 days each time (plus any day in the last 5 weeks with no HRV yet),
so a missed morning fills itself in and the HRV baseline always has history. It only
ever sets the three readiness fields - run data and its Strava source are untouched.

    garmin_sync.py --login     one-time Garmin sign-in on your laptop; copies the session
                               string for the GARMIN_TOKENS secret (password is not stored)
    garmin_sync.py             sync - needs GARMIN_TOKENS and MONGODB_URI in the environment
    garmin_sync.py --dry-run   fetch and print, write nothing

Uses garth, an unofficial Garmin Connect client. The session lasts ~1 year; if Garmin
changes its login flow this can stop working until garth is updated.
"""
import argparse
import datetime as dt
import getpass
import os
import subprocess
import sys
import warnings
from zoneinfo import ZoneInfo

warnings.filterwarnings("ignore")  # macOS LibreSSL notice from urllib3

import garth

LOCAL_TZ = ZoneInfo("America/Denver")   # Fort Collins: "today" is the athlete's morning, not UTC
DAYS_BACK = 7          # always refreshed
BACKFILL_DAYS = 35     # older days are fetched only if they have no HRV yet (keeps baselines full)


def _local_env():
    """~/.config/sub130/garmin.env (KEY=VALUE lines) - lets a re-login update the session directly."""
    path = os.path.expanduser("~/.config/sub130/garmin.env")
    vals = {}
    if os.path.exists(path):
        for line in open(path):
            if "=" in line and not line.startswith("#"):
                k, v = line.strip().split("=", 1)
                vals[k] = v
    return vals


def login():
    email = input("Garmin email: ").strip()
    password = getpass.getpass("Garmin password (used once, not stored): ")
    garth.login(email, password, prompt_mfa=lambda: input("Garmin 2FA code: ").strip())
    token = garth.client.dumps()
    uri = os.environ.get("MONGODB_URI") or _local_env().get("MONGODB_URI")
    if uri:
        from pymongo import MongoClient
        MongoClient(uri, serverSelectionTimeoutMS=20000)["sub130"].garmin_session.replace_one(
            {"_id": "session"}, {"_id": "session", "tokens": token,
                                 "at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")}, upsert=True)
        print("Session saved to the app's database - the sync uses it from its next run.")
    try:
        subprocess.run(["pbcopy"], input=token.encode(), check=True)
        where = "It's on your clipboard."
    except Exception:
        where = "Copy the line below."
        print(token)
    print(f"\nSigned in as {garth.client.username}. {where}")
    print("(Only needed the first time: paste it as the GARMIN_TOKENS secret at "
          "https://github.com/renancamponez/trainer/settings/secrets/actions)")


ERRORS = []   # first few Garmin errors this run, reported instead of silently swallowed


def fetch(path, **params):
    try:
        return garth.connectapi(path, params=params or None)
    except Exception as e:
        if len(ERRORS) < 3:
            ERRORS.append(f"{path.split('/')[1]}: {type(e).__name__}: {str(e)[:300]}")
        return None


def readiness(day, user):
    """HRV / resting HR / sleep score for the night ending on `day` (the morning you woke up)."""
    ds = day.isoformat()
    out = {}

    hrv = fetch(f"/hrv-service/hrv/{ds}") or {}
    avg = (hrv.get("hrvSummary") or {}).get("lastNightAvg")
    if avg:
        out["hrvMs"] = float(avg)

    sleep = fetch(f"/wellness-service/wellness/dailySleepData/{user}", date=ds, nonSleepBufferMinutes=60) or {}
    try:
        out["sleepScore"] = int(sleep["dailySleepDTO"]["sleepScores"]["overall"]["value"])
    except (KeyError, TypeError, ValueError):
        pass

    summary = fetch(f"/usersummary-service/usersummary/daily/{user}", calendarDate=ds) or {}
    rhr = summary.get("restingHeartRate") or sleep.get("restingHeartRate")
    if rhr:
        out["restingHr"] = int(rhr)
    return out


def record(db, ok, message, written=0):
    """Last run's outcome, read by the app (and by whoever is debugging)."""
    print(("OK: " if ok else "FAILED: ") + message)
    if db is not None:
        db.garmin_sync_status.replace_one({"_id": "last"}, {
            "_id": "last", "ok": ok, "message": message, "daysWritten": written,
            "at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        }, upsert=True)


def sync(dry_run=False):
    db = None
    if not dry_run:
        from pymongo import MongoClient
        db = MongoClient(os.environ["MONGODB_URI"], serverSelectionTimeoutMS=20000)["sub130"]

    # Prefer the session saved by the previous successful run (kept fresh); fall back to the secret.
    saved = db.garmin_session.find_one({"_id": "session"}) if db is not None else None
    tokens = (saved or {}).get("tokens") or os.environ.get("GARMIN_TOKENS")
    if not tokens:
        record(db, False, "No Garmin session - run scripts/garmin/setup.sh")
        sys.exit(1)
    try:
        garth.client.loads(tokens)
        # First API call (refreshes the short-lived token if needed). Garmin's per-user endpoints want
        # the profile's displayName; the login userName (here an email) gets 403 on the daily summary.
        user = garth.client.profile.get("displayName") or garth.client.username
    except Exception as e:
        o2 = getattr(garth.client, "oauth2_token", None)
        exp = getattr(o2, "expires_at", None)
        record(db, False, f"Garmin sign-in failed ({'saved' if saved else 'secret'} session, access token "
                          f"expired at {dt.datetime.fromtimestamp(exp, dt.timezone.utc):%Y-%m-%d %H:%M}Z): "
                          f"{type(e).__name__}: {str(e)[:400]}" if exp else
                          f"Garmin sign-in failed: {type(e).__name__}: {str(e)[:400]}")
        sys.exit(1)

    today = dt.datetime.now(LOCAL_TZ).date()
    days = [today - dt.timedelta(days=i) for i in range(DAYS_BACK)]
    if db is not None:
        older = [today - dt.timedelta(days=i) for i in range(DAYS_BACK, BACKFILL_DAYS)]
        have = {d["_id"] for d in db.daylogs.find(
            {"_id": {"$in": [d.isoformat() for d in older]}, "hrvMs": {"$ne": None}}, {"_id": 1})}
        days += [d for d in older if d.isoformat() not in have]
    print(f"--- sync for {user}, {today} (America/Denver), {len(days)} day(s) ---")
    found = 0
    for day in days:
        vals = readiness(day, user)
        if not vals:
            print(f"{day}  no Garmin data yet")
            continue
        found += 1
        if db is not None:
            # Only the readiness fields; creates a bare log on rest days so they still get a score.
            db.daylogs.update_one({"_id": day.isoformat()}, {"$set": vals}, upsert=True)
        print(f"{day}  {vals}")

    if found == 0:
        record(db, False, "Garmin returned no readiness for the last 7 days. " + " | ".join(ERRORS))
        sys.exit(1)
    if db is not None:
        # Save the (possibly refreshed) session so the next run starts from a fresh token.
        db.garmin_session.replace_one({"_id": "session"}, {
            "_id": "session", "tokens": garth.client.dumps(),
            "at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")}, upsert=True)
    record(db, True, f"{found} day(s) synced" + (f"; errors: {' | '.join(ERRORS)}" if ERRORS else ""), found)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--login", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    login() if a.login else sync(a.dry_run)
