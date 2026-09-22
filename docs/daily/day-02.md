# Day 02 - Step 1 verification scaffolding

**Date:** 2026-09-22
**Roadmap step:** 1 (finishing) - no new features

## Objective

Make Step 1 actually verifiable. The authoring machine cannot run anything, so
CI and the deployed environments are the only evidence the code works. The
working agreement is now: generate → push → CI green → deploy → smoke test →
next step; a red gate is fixed before anything new is built.

That turned deployability into a Step 1 prerequisite rather than a Step 15 task,
and it exposed three things in yesterday's code that would have failed that
loop.

## Problems found and fixed

1. **CI could not have passed.** The frontend job used `npm ci` and
   `setup-node`'s npm cache, and both require a committed `package-lock.json`.
   That file can only be produced by running `npm install`, which this machine
   cannot do — so the very first CI run would have failed before installing
   anything. The job now uses `npm install`, with a comment explaining how to
   switch back once a lockfile exists. A `npm run typecheck` step was added,
   since type errors are otherwise only caught by the build.

2. **A missing API URL rendered a blank white page.** `config.ts` threw during
   module initialisation if `VITE_API_BASE_URL` was unset. On a freshly
   configured Vercel project — the single most likely mistake — the result
   would have been a white screen with the real reason hidden in the console.
   The value is now reported as missing and surfaced in the UI.

3. **The backend would not have bound the right port.** It read only
   `SERVER_PORT`, while Render and most PaaS providers inject `PORT`. The health
   check would have failed and the deploy would have been marked unhealthy.

## Work completed

**Diagnosability**

- `config.ts` no longer throws; it exposes `isApiConfigured`.
- `api.ts` gained `ApiNotConfiguredError` and now wraps `fetch` so a rejected
  request reports "unreachable, asleep, or CORS" instead of a bare
  `TypeError: Failed to fetch`, which is what a CORS rejection actually looks
  like in the browser.
- The home page now renders an environment-check panel showing the API base URL
  and reachability. This is the frontend smoke test for every environment.

**Deployability**

- `backend/Dockerfile` — multi-stage (Maven/JDK 21 build, JRE Alpine runtime),
  dependency layer cached separately from sources, runs as a non-root user,
  `-XX:MaxRAMPercentage=75` for small containers. Tests are deliberately not
  run in the image build; that is CI's job and would need a database here.
- `server.port` now reads `${PORT:${SERVER_PORT:8080}}`.
- `DB_URL_PARAMS` appended to the JDBC URL, so a managed database reached over
  the public internet can be given `?sslmode=require` without a code change.
- `render.yaml` blueprint: Docker web service plus a PostgreSQL instance, with
  database credentials injected via `fromDatabase`, health check on
  `/actuator/health`, `DB_POOL_SIZE=5` for a small free instance, and
  `CORS_ALLOWED_ORIGINS` marked `sync: false` so no wrong origin is committed.
- `frontend/vercel.json` rewrites every path to `index.html`, which
  client-side routing requires.

**Documentation**

- `docs/VERIFICATION.md` — the loop, what to paste when a gate fails, and the
  Step 1 smoke tests (curl commands, the Flyway history query, the frontend
  checks, and the CORS/env-var troubleshooting order).
- `docs/DEPLOYMENT.md` — one-time Render and Vercel setup, including the
  monorepo root-directory setting, the internal-vs-external database host, free
  services sleeping, free databases expiring, and why `VITE_` variables are
  build-time and public.
- `TODO.md` updated with a Step 1b section and the current verification
  checklist.

## Files created

```
backend/Dockerfile
render.yaml
frontend/vercel.json
docs/VERIFICATION.md
docs/DEPLOYMENT.md
docs/daily/day-02.md
```

## Files modified

```
.github/workflows/ci.yml                         npm install, typecheck step
backend/src/main/resources/application.yml       PORT, DB_URL_PARAMS
frontend/src/lib/config.ts                       no throw on missing env var
frontend/src/lib/api.ts                          config + network error types
frontend/src/pages/HomePage.tsx                  environment-check panel
frontend/src/styles.css                          panel styles
TODO.md                                          Step 1b + current checklist
```

## Decisions

1. **CI is the first gate, not the deployment.** A compile or type error should
   never reach a provider's build queue.
2. **Deployment configuration is committed** (`render.yaml`, `vercel.json`,
   `Dockerfile`) rather than configured only in dashboards, so the setup is
   reviewable and reproducible. Secrets and origins stay out of it.
3. **Infrastructure stays portable.** A plain Dockerfile means Render is a
   choice, not a dependency; if its free tier changes we move hosts, not code.
4. **Diagnosability counts as a feature under this constraint.** When the only
   feedback comes from a deployed environment, an error message that names the
   likely cause saves a whole push-and-wait cycle. This is why the environment
   panel exists.
5. **No wildcard CORS.** Preview deployments get added to the allowlist
   explicitly when needed.

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `mvn`, `java`,
`npm`, `node`, `docker`, `docker build`, `python`, `pip`. The Dockerfile has
not been built, the workflow has not been run, and nothing has been deployed.
No claim is made that CI passes or that either deployment succeeds — that is
precisely what the next push is for.

## Known limitations

- Still no `package-lock.json`, so frontend dependency resolution is not
  reproducible between CI runs and Vercel builds. This is the main remaining
  weakness in Step 1; fix it the first time you are on a permitted machine.
- Dependency versions remain unverified against a registry.
- `render.yaml` is written from knowledge of Render's schema and free tier.
  Plan names and free-tier availability change; the blueprint may need editing,
  and Render may reject it outright on the first try.
- No Dockerfile for the frontend — Vercel builds it directly, so one is not
  needed yet.
- CI does not deploy; the providers deploy on push themselves.
- No linting or formatting tooling yet.

## Next step

Push and work the Step 1 checklist in `docs/VERIFICATION.md`. Step 2 (backend
architecture) starts only once CI is green and both environments answer
correctly.
