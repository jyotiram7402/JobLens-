# Verification loop

The authoring machine is a locked-down office machine: no `mvn`, `npm`, `node`,
`docker` or `python` may be run there. Nothing in this repository is ever
verified locally. **CI and the deployed environments are the only proof that the
code works.**

So every step follows the same loop, and a step is not finished until the loop
closes.

## The loop

1. **Generate** — code and configuration are written on the office machine.
2. **Push** — commit and push to `main` (or a branch and open a PR).
3. **Gate 1: CI** — GitHub Actions must be green. Backend `mvn verify` compiles
   and runs the tests against a real PostgreSQL service container; the frontend
   type-checks and builds.
4. **Gate 2: deploy** — the backend to Render, the frontend to Vercel.
5. **Gate 3: smoke test** — run the checks for the current step (below).
6. **Green?** move to the next step. **Red?** paste the exact error output —
   the CI log, the Render deploy log, the browser console — and we fix it
   first. No new features on top of a broken step.

Because failures can only be seen at stage 3 or later, expect the first push of
each step to need a fix or two. That is the cost of the constraint, not a
mistake.

## What to paste when something fails

- **CI failure** — the failing job name and the last ~40 lines of its log.
- **Backend deploy failure** — the Render build/deploy log tail, plus whether
  the failure was at build, at startup, or at the health check.
- **Frontend problem** — what the page shows, plus the browser console and
  network tab entry for the failing request.
- **Runtime error** — the `ApiError` body, which always carries `status`,
  `error`, `message` and `path`.

## Per-step smoke tests

### Step 1 — Foundation

Backend (replace `<api>` with the Render URL):

```bash
curl -i <api>/actuator/health
```
Expect `200` and `{"status":"UP"}`. `UP` means Flyway ran and the database is
reachable, because the datasource contributes to health.

```bash
curl -i <api>/api/v1/meta
```
Expect `200` and `{"application":"joblens-api","version":"..."}`.

```bash
curl -i <api>/api/v1/does-not-exist
```
Expect a JSON error body, not an HTML error page.

Database — in the Neon SQL editor:

```sql
\dt
SELECT version, description, success FROM flyway_schema_history;
```
Expect `flyway_schema_history` and `schema_metadata`, with `V1` successful.

Frontend — open the Vercel URL:

- The **Environment check** panel shows the API base URL and
  `API reachable: yes` with the application name and version.
- Reload on a deep link such as `/anything` — it must render the
  "Page not found" page, not a Vercel 404. That proves the SPA rewrite works.
- The browser console shows no CORS error.

If the panel says `not configured`, `VITE_API_BASE_URL` is missing from the
Vercel project — set it and **redeploy** (Vite inlines it at build time, so a
new value needs a new build).

If it says `API reachable: no`, work through, in order: is the Render service
awake (free services sleep, so retry once); does `curl` against the API work at
all; does `CORS_ALLOWED_ORIGINS` on Render exactly match the Vercel origin
(scheme included, no trailing slash).

### Later steps

Each step adds its own section here, listing the endpoints and UI behaviour
that must work before the step is considered done.
