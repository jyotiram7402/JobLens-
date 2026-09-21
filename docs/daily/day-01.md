# Day 01 - Foundation

**Date:** 2026-09-21
**Roadmap step:** 1 - Foundation

## Objective

Establish a clean, production-minded foundation for JobLens V1: monorepo
structure, a Spring Boot backend skeleton wired for PostgreSQL and Flyway, a
React + TypeScript frontend skeleton, a placeholder for the future AI service,
local Docker infrastructure, a CI workflow, and the core documentation.

No business features. No authentication, company/job APIs, matching, OCR,
camera, Kafka, Redis, notifications or scraping.

## Starting point

The repository was empty and not yet a git repository, so nothing needed to be
preserved or integrated with.

## Work completed

**Repository**

- Monorepo layout: `backend/`, `frontend/`, `ai-service/`, `docker/`, `docs/`,
  `.github/workflows/`.
- `.gitignore` covering Java/Maven, Node, Python, IDE and OS artefacts, and
  `.env` files with an allowance for `.env.example`.
- Root `.env.example` documenting every backend/infrastructure variable.

**Backend**

- Maven project, Java 21, Spring Boot 3.3.5, artifact `com.joblens:joblens-api`.
- Dependencies limited to Web, Actuator, Validation, JDBC, Flyway (core +
  PostgreSQL support), the PostgreSQL driver, and the test starter.
- `JobLensApplication` entry point, packages `config`, `common/web`, `health`.
- `application.yml`: datasource, Flyway, Actuator health probes, logging and
  CORS, all driven by environment variables with local-development defaults.
- `V1__baseline.sql`: a `schema_metadata` marker table so Flyway owns the
  schema from the first deployment. No business tables.
- `MetaController` exposing `GET /api/v1/meta` (application name and version).
- `ApiError` record and `GlobalExceptionHandler` establishing one error shape
  for the whole API, including field-level validation details.
- `CorsConfig` reading allowed origins from configuration.
- `JobLensApplicationTests` context smoke test with a `test` profile.

**Frontend**

- Vite + React 18 + TypeScript project, strict compiler settings.
- React Router with a layout route, home page and not-found page, structured so
  `/scan`, `/companies`, `/jobs`, `/dashboard` and `/profile` slot in later.
- `lib/config.ts` reads `VITE_API_BASE_URL` and fails loudly if it is missing;
  `lib/api.ts` is the single fetch wrapper and typed error.
- Home page calls `/api/v1/meta` and reports backend connectivity.
- Minimal stylesheet with light/dark support. `frontend/.env.example`.

**AI service**

- README only. Documents the future purpose, the stateless inference
  responsibility boundary, the planned synchronous HTTP contract with Spring
  Boot, and why OCR/AI is separated from the Java backend. No Python written.

**Docker**

- `docker/docker-compose.yml` starting PostgreSQL 16 only, with a named volume
  and a health check.
- `docker/README.md` explaining usage and why application images come later.

**CI**

- `.github/workflows/ci.yml` with two jobs: backend (`mvn verify` against a
  PostgreSQL service container) and frontend (`npm ci` + `npm run build`).

**Documentation**

- `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `TODO.md`, this log, and
  `docs/decisions/0001-modular-monolith.md`.

## Files created

```
.gitignore
.env.example
README.md
ARCHITECTURE.md
ROADMAP.md
TODO.md
.github/workflows/ci.yml
ai-service/README.md
docker/README.md
docker/docker-compose.yml
docs/daily/day-01.md
docs/decisions/0001-modular-monolith.md
backend/pom.xml
backend/src/main/java/com/joblens/api/JobLensApplication.java
backend/src/main/java/com/joblens/api/config/CorsConfig.java
backend/src/main/java/com/joblens/api/common/web/ApiError.java
backend/src/main/java/com/joblens/api/common/web/GlobalExceptionHandler.java
backend/src/main/java/com/joblens/api/health/MetaController.java
backend/src/main/resources/application.yml
backend/src/main/resources/db/migration/V1__baseline.sql
backend/src/test/java/com/joblens/api/JobLensApplicationTests.java
backend/src/test/resources/application-test.yml
frontend/package.json
frontend/tsconfig.json
frontend/vite.config.ts
frontend/index.html
frontend/.env.example
frontend/src/main.tsx
frontend/src/styles.css
frontend/src/routes/router.tsx
frontend/src/components/layout/AppLayout.tsx
frontend/src/pages/HomePage.tsx
frontend/src/pages/NotFoundPage.tsx
frontend/src/lib/config.ts
frontend/src/lib/api.ts
```

No files were modified, since the repository started empty.

## Architecture decisions

1. **Modular monolith for the backend.** One product, one database, one team.
   Boundaries are enforced by packages rather than processes. Recorded as
   ADR 0001.
2. **Flyway owns the schema, not an ORM.** Forward-only versioned migrations,
   committed to the repository; `ddl-auto` is never used. A baseline migration
   exists so the first deployment has something to apply.
3. **JDBC starter, not JPA, for now.** The foundation needs a `DataSource` so
   Flyway can run; choosing between Spring Data JPA and plain JDBC is a Step 2
   decision that should be made with the company domain in front of us.
4. **Everything configured by environment variable.** No credentials and no
   production hostnames in the repository, on either side.
5. **One error shape for the whole API**, established before the first real
   endpoint so it is never retrofitted.
6. **API versioned from the first endpoint** (`/api/v1/**`).
7. **Operational health stays on Actuator**; `/api/v1/meta` is a separate,
   deliberately trivial endpoint for the frontend, so we never expose internal
   health details to the browser.
8. **Docker for infrastructure only at this stage.** Application images belong
   to the production-readiness step.
9. **Tests run against real PostgreSQL**, in Docker locally and as a service
   container in CI, rather than an in-memory database that behaves differently.
10. **No Redis, no Kafka, no microservices.**

## Technologies selected

| Choice                    | Reason |
| ------------------------- | ------ |
| Java 21                   | Current LTS; records and pattern matching keep the code compact. |
| Spring Boot 3.3.5         | Mature, well-documented, requires Java 17+; the exact patch version should be confirmed against the current release. |
| Maven                     | Simpler and more conventional than Gradle for a Spring Boot portfolio project. |
| PostgreSQL 16             | Free, open source, available on every free tier we would consider. |
| Flyway                    | Plain SQL migrations, easy to review and to explain. |
| React 18 + TypeScript     | Required by the project brief; type safety across the API boundary. |
| Vite                      | Fast dev server, simple build, first-class TypeScript support. |
| React Router 6            | Standard routing; the route table is where later pages attach. |
| GitHub Actions            | Free for public repositories and already next to the source. |

## Commands that WOULD be used on a permitted development machine

Infrastructure:

```bash
cp .env.example .env
docker compose -f docker/docker-compose.yml up -d
docker exec -it joblens-postgres createdb -U joblens joblens_test
```

Backend:

```bash
cd backend
mvn --batch-mode verify
mvn spring-boot:run
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/meta
```

Frontend:

```bash
cd frontend
cp .env.example .env.local
npm install
npm run typecheck
npm run build
npm run dev
```

Source control:

```bash
git init
git add .
git commit -m "Step 1: JobLens V1 foundation"
git branch -M main
git remote add origin <repository-url>
git push -u origin main
```

## Commands were NOT executed

**Nothing in this repository has been built, compiled, tested or run.**
Not executed because this is the locked-down office machine: `mvn`, `java`,
`npm`, `node`, `npx`, `docker`, `docker compose`, `python`, `pip`, and `git`.
Every command above is written for the separate permitted development/test
machine. No claim of a successful build, test run or CI run is made anywhere in
this repository.

## Known limitations

- **No `package-lock.json` yet.** It can only be produced by running
  `npm install`. Until it is generated and committed, the CI frontend job will
  fail at `npm ci`. This is the single most likely first CI failure.
- **Dependency versions are unverified.** Spring Boot 3.3.5, React 18.3, Vite 5
  and the React Router version were chosen from knowledge, not resolved against
  a registry. They may need bumping.
- **The context test needs a real database.** `mvn verify` will fail without a
  reachable PostgreSQL on the `test` profile.
- The backend depends on the JDBC starter but has no repositories yet; the
  persistence approach is deliberately still open.
- There is no linting or formatting setup (Checkstyle/Spotless, ESLint,
  Prettier) yet.
- No Dockerfiles for the applications, and no deployment configuration.
- No git repository has been initialised.

## What to verify on the test machine

1. `mvn verify` in `backend/` succeeds with PostgreSQL running.
2. The application starts, Flyway applies `V1__baseline.sql`, and
   `flyway_schema_history` plus `schema_metadata` exist.
3. `/actuator/health` returns `UP` and `/api/v1/meta` returns the app name and
   version.
4. `npm install` succeeds; commit the resulting `package-lock.json`.
5. `npm run build` and `npm run typecheck` pass with no TypeScript errors.
6. With both running, the home page reports a successful API connection, i.e.
   CORS is configured correctly.
7. Pushing to GitHub makes both CI jobs run, and they pass.

## Next step

Step 2 - Backend architecture: package conventions, the persistence decision
(Spring Data JPA vs JDBC), layering rules, DTO/mapping approach, pagination and
versioning conventions, and the testing strategy. Not started.
