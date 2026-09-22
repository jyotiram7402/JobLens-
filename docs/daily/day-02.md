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
  and JVM flags tuned for a small container (see the free-tier section below).
  Tests are deliberately not run in the image build; that is CI's job and would
  need a database here.
- `server.port` now reads `${PORT:${SERVER_PORT:8080}}`.
- `DB_URL_PARAMS` appended to the JDBC URL, so a managed database reached over
  the public internet can be given `?sslmode=require` without a code change.
- `render.yaml` blueprint: Docker web service with the database supplied
  externally, health check on `/actuator/health`, a small connection pool to
  suit a small free database, and every `DB_*` plus `CORS_ALLOWED_ORIGINS`
  marked `sync: false` so nothing sensitive or environment-specific is
  committed.
- `frontend/vercel.json` rewrites every path to `index.html`, which
  client-side routing requires.

**Documentation**

- `docs/VERIFICATION.md` — the loop, what to paste when a gate fails, and the
  Step 1 smoke tests (curl commands, the Flyway history query, the frontend
  checks, and the CORS/env-var troubleshooting order).
- `docs/DEPLOYMENT.md` — one-time Neon, Render and Vercel setup in dependency
  order, including the monorepo root-directory setting, mapping a Neon
  connection string onto the `DB_*` variables, free services sleeping, why
  `VITE_` variables are build-time and public, how to test each layer for free,
  and how to stay inside the limits.
- `TODO.md` updated with a Step 1b section and the current verification
  checklist.

## Free-tier research, and a changed decision

Checked the providers rather than trusting yesterday's plan, and one decision
changed as a result.

**Render's free PostgreSQL expires 30 days after creation** (14-day grace
period, then the database and its data are deleted). That is acceptable for a
throwaway test and wrong for a portfolio project that should still be working
when someone looks at it months later. The database moved to **Neon**, whose
free tier has no expiry: ~0.5 GB storage, 100 compute-hours/month, scale-to-zero,
and exceeding the limits suspends compute rather than deleting data. Supabase
was the other candidate but pauses a project after a week of inactivity —
precisely the traffic pattern a portfolio project has.

`render.yaml` therefore no longer creates a database. All five `DB_*` variables
are `sync: false` so Render prompts for them, and `DB_URL_PARAMS` is set to
`?sslmode=require` because Neon refuses plaintext connections. This also makes
the database provider swappable without touching code.

**The free web service is 512 MB RAM and 0.1 CPU**, which changed the JVM
flags: `MaxRAMPercentage` dropped from 75 to 65 to leave headroom for metaspace,
threads and code cache outside the heap, and `-XX:+UseSerialGC` was added
because a parallel collector on a tenth of a CPU costs more than it saves.
Other limits worth knowing: 750 instance hours/month, sleeps after ~15 minutes
idle and takes up to a minute to wake, 500 build minutes/month.

## A second blocker found

**CI would never have run.** The workflow triggered on `main`, but the local
branch is `master`, so no push would have matched. The triggers now accept both.
Renaming to `main` is still the conventional choice and is noted as optional in
the deployment guide.

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
