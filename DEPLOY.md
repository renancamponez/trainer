# Deploying (free, access from anywhere)

This runs the whole app as **one free service** on Render, backed by a **free MongoDB Atlas**
database, protected by a **password**. Total cost: $0. Budget ~20 minutes.

**One caveat:** Render's free tier sleeps the app after 15 minutes of inactivity, so the first
visit after a quiet spell takes ~30–60 seconds to wake, then it's fast. Fine for personal use.

---

## 1. Put the code on GitHub

Render deploys from a Git repo. From this folder (`sub130-app`):

```bash
git init && git add -A && git commit -m "Sub 1:30 tracker"
```

Create an empty repo on github.com (private is fine), then:

```bash
git remote add origin https://github.com/<you>/sub130.git
git branch -M main
git push -u origin main
```

## 2. Create the database (MongoDB Atlas — free)

1. Sign up at **https://www.mongodb.com/cloud/atlas** and create a **free M0** cluster.
2. **Database Access** → add a database user (username + password). Save them.
3. **Network Access** → Add IP Address → **Allow access from anywhere** (`0.0.0.0/0`).
   (Simplest for Render, whose IPs vary. Your data is still password-protected.)
4. **Connect** → **Drivers** → copy the connection string. It looks like:
   `mongodb+srv://USER:PASSWORD@cluster0.xxxx.mongodb.net/?retryWrites=true&w=majority`
   Insert your DB password, and add the database name `sub130` before the `?`:
   `mongodb+srv://USER:PASSWORD@cluster0.xxxx.mongodb.net/sub130?retryWrites=true&w=majority`

## 3. Deploy on Render (free)

1. Sign up at **https://render.com** and connect your GitHub.
2. **New → Blueprint**, pick your `sub130` repo. Render reads `render.yaml` and creates the service.
3. When prompted (or under the service's **Environment**), set three variables:
   - `MONGODB_URI` = the Atlas string from step 2
   - `APP_USERNAME` = a login name you choose
   - `APP_PASSWORD` = a strong password you choose  ← **this is what turns auth on**
4. **Create / Deploy.** First build takes a few minutes.
5. Open the service URL (`https://sub130-xxxx.onrender.com`). Your browser will ask for the
   username/password once — that's your login. You're in, on any device.

To update later: `git push` — Render redeploys automatically.

---

## Moving your existing data (optional)

Your local logs live in the local Docker Mongo, not Atlas. Two options:

- **Simplest:** start fresh on Atlas and re-sync your runs from Strava (Settings → Strava →
  Sync). Re-enter recent readiness by hand. The seeder adds your first run automatically.
- **Full copy:** with the MongoDB tools installed,
  ```bash
  docker exec sub130-app-mongo-1 mongodump --db sub130 --archive > sub130.dump
  mongorestore --uri "<your Atlas URI>" --archive < sub130.dump
  ```

## Notes

- **Local dev is unchanged.** With no `APP_PASSWORD` set, auth is off, so `docker compose up`
  still runs open on your machine. Auth only switches on when `APP_PASSWORD` is present.
- **Security:** the login is HTTP Basic auth over Render's HTTPS. Good for a private, single-user
  app. It is not hardened multi-user auth — don't share the URL/password widely.
- **Keeping it awake (optional):** a free uptime pinger (e.g. UptimeRobot) hitting `/api/health`
  every 10 min will keep the free instance from sleeping, removing the cold-start wait.
