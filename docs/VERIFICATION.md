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

### Step 2 — Backend architecture

No new business endpoints, so the checks are about the machinery.

```bash
curl -i <api>/api/v1/meta
```
Expect `application`, `version` and `environment` — and an `X-Correlation-Id`
response header on every response.

```bash
curl -i -H "X-Correlation-Id: my-trace-1" <api>/api/v1/meta
```
Expect `my-trace-1` echoed back, proving an inbound trace is honoured.

```bash
curl -i <api>/api/v1/nope
```
Expect a JSON `ApiError` body with `error`, `path` and `traceId` — not an HTML
error page and not Spring's default body.

```bash
curl -i -X DELETE <api>/api/v1/meta
```
Expect `405` in that same `ApiError` shape.

On the deployed backend, `/api/v1/meta` should report
`"environment": "production"`. If it says `dev`, `SPRING_PROFILES_ACTIVE` is not
reaching the container and the service is running with development defaults.

In CI: the backend job must now run three test classes, one of which
(`JobLensApplicationTests`) proves the context loads with JPA and that Flyway
migrated a real database.

### Step 3 — Database + Company

Migration first. In the Neon SQL editor:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```
Expect `V1` and `V2` both successful. `V9001` must **not** appear — that is
dev-only seed data, and its presence means the service is running the dev
profile against a production database.

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'companies'
ORDER BY ordinal_position;
```
Expect `id` as `uuid` — not `bytea` or `character varying`, which would mean the
UUID mapping is wrong — and `created_at`/`updated_at` as
`timestamp with time zone`.

```sql
SELECT indexname FROM pg_indexes WHERE tablename = 'companies';
```
Expect both `ux_companies_normalized_name` and `ux_companies_slug`.

(`\d companies` works in `psql`, but not in a browser SQL editor, which only
runs SQL.)

Create, then create the same company differently cased:

```bash
curl -i -X POST <api>/api/v1/companies -H 'Content-Type: application/json' \
  -d '{"name":"Tata Consultancy Services","industry":"Information Technology"}'
```
Expect `201`, a `Location` header, and `"slug": "tata-consultancy-services"`.

```bash
curl -i -X POST <api>/api/v1/companies -H 'Content-Type: application/json' \
  -d '{"name":"TATA   CONSULTANCY   SERVICES"}'
```
Expect `409` and `"error": "COMPANY_ALREADY_EXISTS"`. This is the normalization
and duplicate detection working end to end; if it returns `201`, they are not.

```bash
curl -i '<api>/api/v1/companies?search=TATA'
curl -i '<api>/api/v1/companies?size=5000'
curl -i <api>/api/v1/companies/not-a-uuid
curl -i <api>/api/v1/companies/00000000-0000-0000-0000-000000000000
```
Expect: a match despite the casing; `400 VALIDATION_ERROR` for the page size;
`400` for the malformed UUID; `404 COMPANY_NOT_FOUND` for the missing one.

Check the response body of a fetch for fields that must **not** be there:
`normalizedName` and `version`. Their presence means an entity is being
serialised directly.

In CI: the backend job now runs five more test classes, two of which need the
PostgreSQL service container.
