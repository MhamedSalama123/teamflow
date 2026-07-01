---
name: run-teamflow
description: Build, launch, and drive the TeamFlow full-stack app (Spring Boot backend + Angular frontend) to see it working in a real browser. Use to run/start TeamFlow, screenshot the app, or verify a UI change end-to-end (e.g. the real-time chat panel, kanban board, AI assistant panel, notifications).
---

# Run TeamFlow

TeamFlow is a two-app monorepo — a Spring Boot backend (`backend/`, port 8080)
and an Angular SPA (`frontend/`, port 4200). Seeing anything in the UI needs
**both running at once**: `ng serve` proxies `/api`, `/uploads`, and `/ws` to the
backend (see `frontend/proxy.conf.json`).

The agent path is a Playwright driver — **`.claude/skills/run-teamflow/driver.mjs`**
— that seeds data through the REST API and drives a real headless Chrome through a
full user flow: it loads the chat panel, uses `@mention` autocomplete, sends a
message, confirms a second message arrives **live over STOMP**, then opens the AI
assistant panel and steps through its three tabs (Summarize / Generate Tasks /
Ask). It writes screenshots you can inspect.

All paths below are relative to the repo root.

## Prerequisites

- **Docker running** — the backend's local run mode starts a throwaway Postgres
  via Testcontainers.
- **JDK 21** (`java -version` → 21) and **Node** (verified on v24).
- **Chrome or Edge installed** — the driver launches your system browser via
  Playwright's `channel`, so no ~150 MB browser download is needed.

## Run (agent path)

### 1. Start the backend (Testcontainers Postgres)

```bash
mkdir -p /tmp/teamflow-run
( cd backend && ./gradlew bootTestRun > /tmp/teamflow-run/backend.log 2>&1 & )
# ready when this returns 401 (auth required):
for i in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/notifications/me)" = "401" ] && echo UP && break
  sleep 3
done
```

`bootTestRun` runs `TestBackendApplication`, which wires a Postgres container in
via `@ServiceConnection`. SMTP is unconfigured, so email verification codes are
**written to the log** — the driver scrapes them from `backend.log`, so keep that
path.

### 2. Start the frontend

```bash
( cd frontend && npm install && npm start > /tmp/teamflow-run/frontend.log 2>&1 & )
for i in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:4200)" = "200" ] && echo UP && break
  sleep 3
done
```

### 3. Install the driver's one dependency

`playwright-core` only (skip its bundled browser — we use system Chrome):

```bash
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
  npm install --prefix .claude/skills/run-teamflow --no-save playwright-core
```

### 4. Run the driver

```bash
PLAYWRIGHT_DIR=.claude/skills/run-teamflow \
BACKEND_LOG=/tmp/teamflow-run/backend.log \
OUT_DIR=.claude/skills/run-teamflow/screenshots \
  node .claude/skills/run-teamflow/driver.mjs
```

Prints `SUCCESS` and writes screenshots to `OUT_DIR`: `01-chat-loaded.png`,
`02-mention-autocomplete.png`, `03-message-sent.png`, `04-realtime.png` (chat), and
`05-ai-summarize-tab.png`, `06-ai-generate-tab.png`, `07-ai-ask-tab.png` (AI panel).
**Open them** — a blank or error frame means it didn't really run. On failure it
writes `99-error.png` and exits 1. The driver fails if the browser logs any console
error.

**AI panel — live output needs a Claude key.** The driver verifies the AI panel
*renders* and its tabs work; it does not click through a live summarize/ask, because
that needs `ANTHROPIC_API_KEY` on the backend. Without a key the `/api/ai/*` calls
return 503 and the panel shows a graceful error (no console error, so the driver
still passes). To see real AI output, start the backend with the key set
(`ANTHROPIC_API_KEY=sk-ant-… ./gradlew bootTestRun …`) and click a tab's button.

Env vars (all optional except `BACKEND_LOG`): `FE_URL` (default
`http://localhost:4200`), `BE_URL` (default `http://localhost:8080`),
`PLAYWRIGHT_DIR`, `OUT_DIR`.

### Stop everything

```bash
pkill -f bootTestRun; pkill -f 'ng serve'   # or, on Windows PowerShell, Stop-Process the java/node PIDs
```

The Testcontainers Postgres is torn down automatically when the backend JVM exits.

## Run (human path)

`cd backend && ./gradlew bootTestRun` and `cd frontend && npm start`, then open
http://localhost:4200 and register a user (grab the verification code from the
backend console — SMTP is off). Fine for clicking around; useless headless, and
it can't prove the real-time round-trip on its own.

## Gotchas

- **Port 8080 may be taken.** On this dev machine an Apache `httpd` (XAMPP) owns
  8080; `bootTestRun` then dies with "Port 8080 was already in use" — but a
  non-Spring server still answers there (generic HTML 404, not our JSON API), so
  don't assume 8080 == our backend. Workaround: run the backend on another port
  and point the proxy at it (revert the proxy edit after):
  ```bash
  ( cd backend && ./gradlew bootTestRun --args='--server.port=8081' > /tmp/teamflow-run/backend.log 2>&1 & )
  sed -i 's/8080/8081/g' frontend/proxy.conf.json   # restart ng serve after; git checkout it when done
  ```
  then pass `BE_URL=http://localhost:8081` to the driver.
- **ESM ignores `NODE_PATH`.** That's why the driver resolves `playwright-core`
  via `PLAYWRIGHT_DIR` (a dir whose `node_modules/` has it) rather than a bare
  import against `NODE_PATH`.
- **Auth is via `localStorage`, not cookies.** The driver seeds
  `teamflow.accessToken` / `teamflow.refreshToken` on the `/login` origin, then
  navigates to `/projects` — no UI login needed. STOMP reads the same token.
- **Chat needs an active member.** `@mention` autocomplete only suggests **active**
  workspace members, so the driver invites Bob and accepts before opening the panel.
- **AI tab selectors need exact match.** `getByRole('button', {name: 'Ask'})` /
  `:has-text("Ask")` also matches the board's **"Add task"** button (substring, case
  insensitive → "add t**ask**"). The driver uses `{ name: 'Ask', exact: true }` for the
  AI tabs.
- **First `/projects` paint is slow.** Angular compiles the lazy route on first
  hit; `wait-for` the `Chat` heading rather than sleeping.
- **Unique users per run.** The driver suffixes emails/usernames with a timestamp
  so re-runs don't hit "email already used".

## Troubleshooting

- `ERR_MODULE_NOT_FOUND: playwright-core` → step 3 wasn't run, or `PLAYWRIGHT_DIR`
  doesn't point at the dir that has `node_modules/playwright-core`.
- `no verification code found in <log>` → `BACKEND_LOG` is wrong, or SMTP is
  actually configured (then codes aren't logged). Confirm the backend log shows
  `Mail is not configured; verification code for … is NNNNNN`.
- Driver hangs on `text=Chat` / `text=color palette` → frontend proxy isn't
  reaching the backend. `curl http://localhost:4200/api/notifications/me` should
  return 401; if it fails, the proxy target/port is wrong (see the 8080 gotcha).
- `could not launch Chrome or Edge` → neither is installed where Playwright's
  `channel` looks; install Chrome.
