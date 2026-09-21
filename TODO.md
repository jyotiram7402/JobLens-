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

## Current

Verification of Step 1 on the permitted development/test machine.

- [ ] Run `mvn verify` in `backend/` against a running PostgreSQL
- [ ] Run `npm install` in `frontend/` and **commit `package-lock.json`**
      (the CI frontend job uses `npm ci` and will fail until it exists)
- [ ] Run `npm run build` and `npm run dev` in `frontend/`
- [ ] Start `docker compose -f docker/docker-compose.yml up -d` and create the
      `joblens_test` database
- [ ] Confirm the home page reports a successful API connection
- [ ] Initialise git, push to GitHub, confirm the CI workflow runs
- [ ] Confirm the pinned Spring Boot, React, Vite and Node versions are the
      ones we want

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
