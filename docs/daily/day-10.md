# Day 10 — Company Tracking

**Date:** 2026-10-08
**Roadmap step:** 10

## Goal

Let a signed-in user track and untrack companies, see whether a company is
tracked, and view their tracked companies — from the company page, a dedicated
list page and the dashboard. End to end, on the existing authentication and
company domain, with the database enforcing one row per user and company.

## Implemented

- A `tracked_companies` table with a unique `(user_id, company_id)` constraint.
- A `tracking` module: entity, repository, service, controller, DTOs.
- Four endpoints, all authenticated, none accepting a user id.
- Idempotent track and untrack, correct under concurrent duplicate requests.
- A Track button on company pages, a `/tracked-companies` page, a navigation
  entry, and the dashboard's tracked-companies tile and strip made real.

### What was inspected first

- **Migrations** ran V1–V6, so this is `V7`.
- **`tracking/`** held only its `package-info.java` boundary marker from step 2.
- **`SecurityConfig`** has `GET /api/v1/companies/**` as `permitAll()`. A new
  `GET /companies/{id}/track` would therefore have been public — the same
  ordering trap the step 7 matching routes hit. Fixed by listing it first.
- **`BaseEntity`** gives every entity a time-ordered UUID, UTC audit timestamps
  and a `version` column, so the migration includes `version` even though the
  brief's suggested schema did not — `ddl-auto: validate` would otherwise fail
  startup.
- **The navigation has no "Companies" entry** because there is no company list
  page, only company detail reached from jobs. No link was added to a page that
  does not exist.
- **The dashboard** already had a tracked-companies tile reading "Coming soon",
  and a test asserting it. Both were changed.

## Backend Changes

```
tracking/
  TrackingController.java          the four endpoints
  TrackingService.java             idempotent track/untrack, status, list
  TrackedCompanyRepository.java    entity graph on the list; single DELETE
  domain/TrackedCompany.java       user -> company, with trackedAt
  dto/TrackingStatusResponse.java  { companyId, tracked, trackedAt }
  dto/TrackedCompanyResponse.java  a card's worth of company + trackedAt
```

**An entity, not a join table on `User`.** It carries data of its own (when
tracking started), and a `Set<Company>` on `User` would make the user module know
about tracking — backwards.

**Idempotency.** Tracking an already-tracked company returns `200` with the
existing relationship; untracking something not tracked returns `204`. A
conflict response would be an error message describing a success.

**Concurrent duplicates, done properly.** A double-click can produce two
requests that both pass the "already tracked?" check. The unique constraint
lets one insert win; the loser gets a violation — and at that moment its
transaction is **rollback-only**. Catching the exception inside an
`@Transactional` method and carrying on looks right and fails at commit. So
`track` uses a `TransactionTemplate`: the insert runs in one transaction, and on
a violation a *new* one reads the winner's row and returns it. If no row exists
afterwards, the violation was something else and is rethrown, not swallowed.

**Status as a dedicated endpoint**, not a `tracked` field on the company:
`GET /companies/{id}` is public and the same for everyone, and per-user state on
it would make one URL return different bodies to different people, couple the
company module to tracking, and cost a query on every anonymous view.

**Module boundary.** `TrackingService` reads `CompanyRepository` and
`UserRepository` directly — the same documented exception `JobService` makes,
because a JPA association needs a managed entity and those services return DTOs.
It never writes users or companies.

**No new error codes.** "Already tracked" and "not tracked" are not errors, and an
unknown company is the existing `COMPANY_NOT_FOUND`.

### A route collision, and a fix in an unexpected place

Writing the security matcher for `GET /companies/*/track` exposed that
`/companies/by-slug/track` has the same shape. Security would treat it as the
authenticated tracking route — so any company whose slug was `track` would have
a public page that anonymous visitors could not open. A company called "Track"
is entirely legal.

Fixed at the source: `SlugGenerator` now treats `track` as reserved, so such a
company gets `track-2`. Cheaper and clearer than regular expressions in the
route mappings, and any future action segment under `/companies/{id}/...` goes in
the same set. There is a unit test and an integration test for it.

## Database Changes

`V7__create_tracked_companies_table.sql`:

```
tracked_companies
  id          UUID        PK (application-generated, time-ordered)
  user_id     UUID        NOT NULL -> users(id)     ON DELETE CASCADE
  company_id  UUID        NOT NULL -> companies(id) ON DELETE CASCADE
  created_at  TIMESTAMPTZ NOT NULL      -- exposed as trackedAt
  updated_at  TIMESTAMPTZ NOT NULL
  version     BIGINT      NOT NULL DEFAULT 0

  UNIQUE (user_id, company_id)
  INDEX  (company_id)
```

- **No separate `user_id` index**: the unique constraint's index leads with
  `user_id`, so it already serves every per-user lookup.
- **`company_id` indexed** for the foreign key — without it, a cascading company
  delete would scan the table.
- **No `(user_id, created_at)` index**: a user tracks tens of companies, so
  sorting them in memory is free, and an index would cost every insert.
- **`CASCADE` on both keys**, the opposite of `jobs.company_id`. A job is real
  data; a tracking row is a preference about two things and meaningless once
  either is gone.
- `created_at` doubles as `trackedAt`; a second column would only be able to
  drift from it.

## API Endpoints

```
POST   /api/v1/companies/{companyId}/track     200 { companyId, tracked: true, trackedAt }
DELETE /api/v1/companies/{companyId}/track     204
GET    /api/v1/companies/{companyId}/track     200 { companyId, tracked, trackedAt }
GET    /api/v1/users/me/tracked-companies      200 page, newest first, size 12 (max 50)
```

`404 COMPANY_NOT_FOUND` for an unknown company on all three per-company
operations; `400` for a malformed UUID; `401` without a token. Full reference:
[../api/tracking.md](../api/tracking.md).

## Frontend Changes

| Change | Detail |
| ------ | ------ |
| `features/tracking/` | `types.ts`, `api.ts`, `TrackButton`, `TrackedCompaniesPage` |
| `endpoints.ts` | `tracking.company(id)`, `tracking.mine` |
| Company page | Track button in the header; "Sign in to track" when signed out |
| `/tracked-companies` | Card grid, immediate untrack, pagination, empty state |
| Dashboard | Tile shows the backend's `totalElements`; a strip of up to six companies with "View all" |
| Navigation | "Tracked companies" for signed-in users |
| Router | `/tracked-companies` behind `RequireAuth` |

`TrackButton`:

- **Two visible states** — "✓ Tracked" plus an explicit "Untrack" — rather than a
  toggle whose label flips and leaves the user guessing which way it will go.
- **Confirmed, not optimistic.** A tick that appears and then reverts is how a
  button loses trust; the honest version costs a few hundred milliseconds.
- **Disabled while in flight**, so a double-click sends one request.
- **Announced** through a live region that always exists — regions that appear
  only when there is something to say are often not announced.
- **Company name in the accessible name** ("Track company Example Company"),
  with the visible text first so the name still contains what is on screen.
- **Self-contained**: it loads its own status and fails alone, so a tracking
  error never breaks the company page.

The list passes `initialTracked`, skipping the status request — every row there
is tracked by definition. Untracking removes the card immediately (the server
has confirmed), and the count is the server's total minus local removals.
Whenever a fetch completes the server's list becomes the truth again, which is
what prevents a removal being subtracted twice; I caught that bug while writing
it. Untracking the last card on a page refetches so later companies flow in, and
a page left empty steps back one.

## Security

- **No endpoint accepts a user id.** The user is always the token's, via
  `CurrentUser`, so there is nothing to tamper with. An integration test sends a
  `userId` parameter and shows it changes nothing.
- **Every tracking query is scoped by user id.** The repository has no method
  that returns tracking rows without one.
- **Rule ordering.** `GET /companies/*/track` is listed before the public
  `GET /companies/**` rule. POST and DELETE fall through to the authenticated
  default. A test asserts the status route returns 401 anonymously and that
  ordinary company reads stay public.
- **Reserved slug**, so the carve-out cannot lock out a real company page.
- **No internals leak**: integrity violations that are not races propagate to
  the global handler, which returns `INTERNAL_ERROR` with a trace id and nothing
  else.

## Tests Added

**Backend**

| Class | Covers |
| ----- | ------ |
| `TrackedCompanyRepositoryTest` | `trackedAt` recorded; **the database rejects a duplicate pair**; different users may track the same company; lookup by user and company; listing returns only the given user's rows; delete removes only the named relationship and never the company; deleting nothing removes nothing |
| `TrackingServiceTest` | Track; track twice inserts nothing; **the race path returns the winner's row**; a non-race violation is rethrown; unknown company 404; untrack; untrack when not tracked; untrack 404; untrack never deletes the company; status tracked and not tracked; status 404; list asks only for the caller's rows. Uses a real `TransactionTemplate` on a mocked transaction manager, which is what makes the race path testable without a database |
| `TrackingIntegrationTest` | All four endpoints 401 anonymously; **the status route is not swallowed by the public companies rule**; company reads stay public; **a company called "Track" gets `track-2` and keeps a public page**; track then status; tracking twice still leaves one row; untrack twice is harmless and the company survives; unknown company 404 on all three operations; malformed id 400; **users cannot see each other's lists or statuses**; page size capped |
| `SlugGeneratorTest` (updated) | `track` is never issued as a slug |

**Frontend**

| File | Covers |
| ---- | ------ |
| `TrackButton.test.tsx` | Signed-out sign-in link and no status request; loads state; tracked state in words; track then announce; untrack then offer again; **disabled in flight so a double-click sends one request**; `initialTracked` skips the status request; failure shows an alert and keeps the previous state |
| `TrackedCompaniesPage.test.tsx` | Lists with links and count; loading; empty state with a link to jobs; error with retry; **untracking removes the card and updates the count without refetching**; no per-card status requests |
| `DashboardPage.test.tsx` (updated) | The tile shows the backend's total, not the preview length; the strip links to companies; empty state; no "Coming soon" |
| `CompanyDetailPage.test.tsx` (updated) | Sign-in prompt when signed out; track button when signed in; **the company page still works when tracking status fails** |

## Commands to Run on Development/Test Machine

**NOT EXECUTED ON THE OFFICE MACHINE**

```bash
docker compose -f docker/docker-compose.yml up -d
docker exec -it joblens-postgres createdb -U joblens joblens_test
```

```bash
cd backend && mvn --batch-mode clean verify
```

```bash
cd backend && mvn spring-boot:run
```

```bash
cd frontend && npm install && npm run typecheck && npm test && npm run build && npm run dev
```

```sql
-- V7 applied, and the constraint exists
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT conname FROM pg_constraint WHERE conrelid = 'tracked_companies'::regclass;
```

Manual checks are listed in [../VERIFICATION.md](../VERIFICATION.md) under
step 10.

## Known Limitations

- **Nothing here has been compiled, migrated or tested.** Commands were not
  executed on the office machine.
- **Only companies can be tracked.** The roadmap originally grouped job tracking
  and application status into step 10; this step built company tracking, as
  briefed, and the rest is recorded in `TODO.md`.
- **The concurrent-duplicate path is unit-tested, not load-tested.** The
  integration test sends two sequential requests; two genuinely simultaneous
  ones would need a multi-threaded test against the real database.
- **`TransactionTemplate` is assumed to be auto-configured.** Spring Boot
  provides one when there is a single transaction manager, which there is — but
  if not, startup fails with a missing-bean error that names it.
- **The tracked-list count is local after untracking.** It is the server's total
  minus removals since the last fetch: correct for this tab, and corrected by
  the next fetch if another tab changed things.
- **No "track" control on job cards** or the job page's company panel yet — only
  on the company page and the tracked list.
- **Steps 1–10 have still never been through CI or a deployment.**

## Next Step

Step 10 is complete.

Do not implement Step 11 yet.
