# TODO

Working checklist. Step numbers refer to [ROADMAP.md](ROADMAP.md).

## Completed

### Step 1 - Foundation

- [x] Monorepo structure (`backend`, `frontend`, `ai-service`, `docker`, `docs`)
- [x] `.gitignore` covering Java, Node, Python, IDE and secrets
- [x] Spring Boot 3.3 / Java 21 Maven project with a minimal dependency set
- [x] Application entry point and package layout (`config`, `common`, `health`)
- [x] Environment-based PostgreSQL configuration, no committed credentials
- [x] Flyway configured with a `V1__baseline.sql` migration
- [x] Actuator health endpoint, `/api/v1/meta` endpoint
- [x] Uniform API error shape and global exception handler
- [x] CORS configuration driven by environment variable
- [x] Spring context smoke test plus `test` profile
- [x] React 18 + TypeScript + Vite frontend with routing and a layout shell
- [x] Centralised API client and environment-based API base URL
- [x] Home page that reports backend connectivity
- [x] `docker/docker-compose.yml` for local PostgreSQL
- [x] GitHub Actions CI workflow (backend build/test, frontend install/build)
- [x] README, ARCHITECTURE, ROADMAP, TODO, `docs/daily/day-01.md`, ADR 0001

### Step 1b - Verification scaffolding

Since nothing can be run locally, CI and the deployed environments are the only
proof the code works. This made them a prerequisite, not a final step.

- [x] CI frontend job no longer requires a committed `package-lock.json`
- [x] Frontend type-check added to CI
- [x] Missing `VITE_API_BASE_URL` reports itself in the UI instead of rendering
      a blank white page
- [x] API client distinguishes unreachable/CORS failures from HTTP errors
- [x] Home page shows an explicit environment-check panel
- [x] Backend honours the `PORT` variable injected by PaaS providers
- [x] `DB_URL_PARAMS` for provider-specific JDBC options (e.g. `sslmode`)
- [x] `backend/Dockerfile` (multi-stage, non-root)
- [x] `render.yaml` blueprint for backend + PostgreSQL
- [x] `frontend/vercel.json` SPA rewrite
- [x] `docs/VERIFICATION.md` and `docs/DEPLOYMENT.md`
- [x] Verified current Render / Vercel / Neon free-tier terms (2026-09-22) and
      moved the database to Neon, because Render's free PostgreSQL expires
      after 30 days
- [x] JVM flags tuned for a 512 MB / 0.1 CPU free container
- [x] CI triggers on `master` as well as `main`, so a push actually runs it

## Current

Closing the Step 1 verification loop — see
[docs/VERIFICATION.md](docs/VERIFICATION.md).

Setup instructions: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

- [ ] Push to GitHub and get **CI green** (backend + frontend jobs)
- [ ] Create the Neon project; collect the five `DB_*` values
- [ ] Create the Render blueprint; confirm `/actuator/health` returns `UP`
- [ ] Confirm `flyway_schema_history` shows `V1` applied successfully
- [ ] Deploy the frontend to Vercel with root directory `frontend` and
      `VITE_API_BASE_URL` set
- [ ] Set Render `CORS_ALLOWED_ORIGINS` to the Vercel origin and redeploy
- [ ] Confirm the environment-check panel reports `API reachable: yes`
- [ ] Confirm a deep-link reload renders the app 404, not a CDN 404
- [ ] Decide whether to rename `master` to `main`
- [ ] Once `npm install` has run somewhere permitted, commit
      `package-lock.json` and switch CI back to `npm ci` + npm cache
- [ ] Confirm the pinned Spring Boot, React, Vite and Node versions resolve

## Next

### Step 2 - Backend architecture

- [ ] Agree the package-per-domain convention and write it down
- [ ] Decide the persistence approach (Spring Data JPA vs JDBC) and record an ADR
- [ ] Define the controller / service / repository layering rules
- [ ] Decide the DTO and mapping approach
- [ ] Define the API versioning and pagination conventions
- [ ] Define the testing strategy (unit vs integration, database in tests)
- [ ] Add a typed not-found / conflict exception family to `common`

### Step 3 - Database + Company

- [ ] `V2__companies.sql` migration
- [ ] Company domain module
- [ ] Company REST API
- [ ] Seed data for development
- [ ] Integration tests

## Future

Steps 4 to 15 in [ROADMAP.md](ROADMAP.md): authentication, jobs, search,
matching, frontend, dashboard, tracking, scan, OCR/AI, AI job intelligence,
production readiness, deployment. V2/V3 items are out of scope.
