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

### Step 4 — Authentication + User profile

**Set `JWT_SECRET` on Render before deploying** (`openssl rand -base64 48`).
Without it the service will not start, by design — that is the first thing to
check if the deploy fails at startup rather than at build.

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```
Expect `V3` applied. Then confirm the tables exist:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_name LIKE 'user%' ORDER BY table_name;
```

Register and log in:

```bash
curl -i -X POST <api>/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"SecurePassword123!","firstName":"Jo","lastName":"Doe"}'
```
Expect `201`, a `user` object, and **no `accessToken`** — registration does not
log you in. Check the body contains nothing password-shaped.

```bash
TOKEN=$(curl -s -X POST <api>/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"SecurePassword123!"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
```

```bash
curl -i <api>/api/v1/users/me -H "Authorization: Bearer $TOKEN"
```
Expect `200` with `profile` present and `skills` an empty array.

Now the negative cases, which matter more:

```bash
curl -i <api>/api/v1/users/me
curl -i <api>/api/v1/users/me -H "Authorization: Bearer not.a.token"
curl -i -X POST <api>/api/v1/companies -H 'Content-Type: application/json' -d '{"name":"X"}'
```
Expect `401 UNAUTHENTICATED`, `401 TOKEN_INVALID`, and `401` — company writes are
protected now. Company **reads** must still work without a token:

```bash
curl -i '<api>/api/v1/companies?search=tata'
```

Account enumeration — these two must be byte-identical apart from `traceId`:

```bash
curl -s -X POST <api>/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"definitely wrong here"}'
curl -s -X POST <api>/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"nobody@example.com","password":"definitely wrong here"}'
```

Profile, including deduplication:

```bash
curl -i -X PUT <api>/api/v1/users/me/profile -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"headline":"Software Engineer","yearsOfExperience":2,"remotePreference":"HYBRID","skills":["Java","java","Spring Boot"]}'
```
Expect `200` with **two** skills, not three.

```bash
curl -i -X PUT <api>/api/v1/users/me/profile -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"yearsOfExperience":-1}'
```
Expect `400` with `details.yearsOfExperience`.

Finally, confirm no password ever reached the database in readable form:

```sql
SELECT email, left(password_hash, 8) AS hash_prefix FROM users;
```
Expect `{bcrypt}` as the prefix. If you see anything resembling a password,
stop and tell me.

In CI: three more test classes, one of which runs the full filter chain against
the PostgreSQL service container.

### Steps 5 and 6 — Jobs, search and filtering

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```
Expect `V4` and `V5` applied. `V9001`/`V9002` must **not** appear in production.

```sql
SELECT indexname FROM pg_indexes WHERE tablename = 'jobs';
```
Expect `ix_jobs_company_id` and `ix_jobs_active_posted_at`.

Create a job (needs a token from step 4 and a company id from step 3):

```bash
curl -i -X POST <api>/api/v1/jobs -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"companyId":"<company-uuid>","title":"Java Backend Developer",
       "description":"Build REST services.","location":"Pune, Maharashtra, India",
       "employmentType":"FULL_TIME","workMode":"HYBRID","experienceMin":2,"experienceMax":5}'
```
Expect `201` with a `Location` header and a nested `company` object.

Now the search. Each of these should behave as described:

```bash
curl -s '<api>/api/v1/jobs'                                   # active only, newest first
curl -s '<api>/api/v1/jobs?search=JAVA'                       # case-insensitive
curl -s '<api>/api/v1/jobs?location=pune'                     # substring match
curl -s '<api>/api/v1/jobs?workMode=REMOTE'
curl -s '<api>/api/v1/jobs?search=java&location=Pune&workMode=HYBRID'
curl -s '<api>/api/v1/jobs?experienceMin=2'
curl -s '<api>/api/v1/jobs?sort=title,asc&size=3'
curl -s '<api>/api/v1/jobs?search=cobol'                      # 200 with empty content
```

The last one matters: an unmatched search is a **200 with `"content": []`**, not
a 404. And every response must carry `hasNext` / `hasPrevious`.

Rejections — each should be a `400` except the last, which is a `401`:

```bash
curl -i '<api>/api/v1/jobs?employmentType=PERMANENT'
curl -i '<api>/api/v1/jobs?size=1000000'
curl -i '<api>/api/v1/jobs?sort=salary,desc'
curl -i '<api>/api/v1/jobs?experienceMin=5&experienceMax=2'
curl -i '<api>/api/v1/jobs?companyId=not-a-uuid'
curl -i '<api>/api/v1/jobs?active=false'
```

**The N+1 check.** Run locally with the dev profile (`show-sql: true`), request
a page of jobs from several different companies, and read the SQL log. Expect
**one** select for the page plus **one** count. A select per result row means
the `@EntityGraph` is not being applied — that is the single most likely defect
in this step.

Locally, confirm the index is used once there is data:

```sql
EXPLAIN ANALYZE
SELECT * FROM jobs WHERE active = true ORDER BY posted_at DESC, id LIMIT 20;
```
On a nearly empty table a sequential scan is correct and not a failure.

In CI: five more test classes, one of which exercises the whole search against
the PostgreSQL service container.

### Step 7 — Job matching

The migration is destructive (it drops `user_skills`), so check it first:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT count(*) AS skills FROM skills;
SELECT count(*) AS links  FROM user_profile_skills;
SELECT to_regclass('user_skills');   -- expect NULL: the old table is gone
```

If any profile had skills before this deploy, `links` must be at least as large
as the number of those rows. If it is zero and skills existed, the backfill
failed silently — stop and tell me.

**Security ordering first**, because it is the one that matters:

```bash
curl -i '<api>/api/v1/jobs/recommended'
curl -i '<api>/api/v1/jobs/00000000-0000-0000-0000-000000000000/match'
```
Both must be `401 UNAUTHENTICATED`. A `200` here means the matching routes are
being swallowed by the public `GET /api/v1/jobs/**` rule, and every user's match
results are readable anonymously. And ordinary discovery must still work:

```bash
curl -i '<api>/api/v1/jobs'
```

Now a real match. Set a profile, create a job with skills, and score it:

```bash
curl -s -X PUT <api>/api/v1/users/me/profile -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"skills":["Java","Spring Boot","Docker"],"yearsOfExperience":3,
       "preferredRoles":["Java Backend Developer"],"preferredLocations":["Pune"],
       "remotePreference":"HYBRID"}'
```

```bash
curl -s -X POST <api>/api/v1/jobs -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"companyId":"<company-uuid>","title":"Java Backend Developer",
       "location":"Pune, Maharashtra, India","employmentType":"FULL_TIME",
       "workMode":"HYBRID","experienceMin":2,"experienceMax":5,
       "skills":["Java","Spring Boot","PostgreSQL"]}'
```

```bash
curl -s "<api>/api/v1/jobs/<jobId>/match" -H "Authorization: Bearer $TOKEN"
```

Expect `scored: true`, a `breakdown` with all five criteria, `missingSkills`
containing exactly `PostgreSQL`, and an `explanation` array. **Read the
explanation**: it must not claim you have PostgreSQL.

Case-insensitivity through the shared table — set skills as `["java","spring
boot"]` and re-match; both must still count as matched.

Missing-data handling:

```bash
# A brand new account with an empty profile
curl -i "<api>/api/v1/jobs/<jobId>/match" -H "Authorization: Bearer $NEW_TOKEN"
```
Expect `422 PROFILE_NOT_READY`, not a score of 0.

Recommendations:

```bash
curl -s '<api>/api/v1/jobs/recommended?page=0&size=20' -H "Authorization: Bearer $TOKEN"
curl -i '<api>/api/v1/jobs/recommended?size=5000' -H "Authorization: Bearer $TOKEN"
```
The first must be sorted by `score` descending and carry `hasNext`/`hasPrevious`;
the second must be a `400`.

**Check `/jobs/recommended` is not shadowed by `/jobs/{id}`.** If it returns
`400` complaining about a UUID, Spring is routing it to the detail endpoint.

**The N+1 check.** Locally with `show-sql: true`, request recommendations and
read the SQL log: expect roughly one query for the candidate jobs, one for their
skills, and one for the profile — not one per job and not one per job's skills.

In CI: three more test classes, one of which drives the whole matching flow
through the real filter chain.

### Step 8 — React frontend foundation

CI first. The frontend job now runs `typecheck`, `test` and `build`; the type
check is the one most likely to fail, because `tsconfig.json` has
`noUnusedLocals`, `noUnusedParameters` and `verbatimModuleSyntax` on and a
stray import is enough.

Locally, with the backend running:

```bash
cd frontend && cp .env.example .env.local && npm install
```
**Commit the `package-lock.json` this produces.** It still does not exist, which
is the only reason CI uses `npm install` rather than `npm ci`.

```bash
npm run typecheck && npm test && npm run build && npm run dev
```

Then open `http://localhost:5173` and check:

- the **Environment check** reports `API reachable: yes` with the application
  name, version and environment. This is the first time that check has ever had
  a chance to succeed, so it is also the first real proof the two halves of the
  project can talk to each other;
- every navigation link reaches its placeholder page;
- `/jobs/abc` and `/companies/abc` render and echo the route parameter back,
  which proves route params are wired;
- an unknown path renders the not-found page rather than a blank screen;
- press Tab from the top: a **Skip to content** link appears first, and every
  control thereafter shows a visible focus ring;
- narrow the window to phone width — the navigation moves above the content and
  nothing scrolls sideways.

If the environment check says `not configured`, `VITE_API_BASE_URL` is missing.
Remember it is the **origin** (`http://localhost:8080`), not the API root —
`/api/v1` is added in code, and including it would produce
`/api/v1/api/v1/meta`.

On Vercel, the same checks against the deployed URL, plus a reload of a deep
link such as `/jobs/abc` to confirm the SPA rewrite still works.

### Step 9 — Dashboard and the company/job UI

This is the first step where the two halves of the project actually talk to each
other, so most of it can only be checked by using it.

Run the backend on the `dev` profile (seed data loads automatically) and the
frontend on 5173, then work through this in order:

1. **Register** at `/register`. It should sign you in and land on `/dashboard` —
   the backend issues no token on registration, so the app logs you in with a
   second call.
2. The dashboard should greet you **by your real name**, show
   `No recommendations yet` with a **Complete profile** link, and show profile
   completion well under 100% naming the missing fields.
3. **Fill in the profile**: skills `Java, Spring Boot, Docker`, 3 years,
   preferred location `Pune`, working arrangement `Hybrid`. Save.
4. Back on the dashboard: recommendations should now appear **with real scores**,
   and completion should rise. If recommendations are still empty, the seeded
   jobs have no skills — check `V9003` applied.
5. **Open a job.** The match panel should show a score, a per-criterion table,
   matched and missing skills, and the backend's own explanation sentences. An
   unscored criterion must read **"not compared"**, never `0/50`.
6. **Search.** Go to `/jobs`, type `java`, add a location and a work mode. The
   URL must update as you go. Then **refresh the page** — the filters must
   survive — and press **Back**.
7. **Pagination** (needs more than 20 jobs): change page and confirm the filters
   are preserved in the URL.
8. **Sign out** and open the same job. It must still render, with the match
   panel inviting you to sign in. Job and company browsing is public by design.
9. **Phone width.** Narrow the window below ~768px: the sidebar should collapse
   to a ☰ toggle, and nothing should scroll sideways.

Things that should specifically *not* happen:

- No page should show a number nobody computed. Tracked companies says
  "Coming soon"; a job with no match says why.
- A failing match must not break the job page.
- An unmatched search must be an empty state, not an error.

Watch the network tab while typing in the search box: there should be roughly
**one request per pause**, not one per keystroke.

In CI: five more frontend test files. The frontend job runs `typecheck`, `test`
and `build`, and with this much new TypeScript the type check is the most likely
place to fail.
