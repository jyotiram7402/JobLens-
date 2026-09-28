# Day 06 - The Job domain and job search

**Date:** 2026-09-24
**Roadmap steps:** 5 (Jobs) and 6 (Search + filtering)

> Day numbers stopped matching step numbers on day 2; [README.md](README.md)
> maps them.

## Objective

Make `GET /api/v1/jobs` a clean, useful, scalable V1 search API.

## Existing implementation reviewed — and a correction

Step 6 was briefed as "improve the existing Job implementation, do not blindly
rewrite step 5". **Step 5 did not exist.** The inspection found:

- `com/joblens/api/job/` contained only `package-info.java`, the boundary marker
  written in step 2 — no entity, repository, service, controller or DTOs
- migrations ran `V1`, `V2`, `V3`; there was no jobs table
- `git log` ended at `Step 4: authentication, JWT and the user career profile`
- the roadmap still listed step 5 as not done

So there was nothing to preserve or improve. Rather than guess, I stopped and
asked; the answer was to build both steps in one session, kept as two commits so
the history still reads incrementally.

What *was* reviewed and reused:

- `BaseEntity` — UUID primary keys, UTC `Instant` audit timestamps, optimistic
  locking. Jobs follow it unchanged.
- `Company` — the association target. No company data is duplicated into jobs.
- `PageResponse` — already the list envelope; extended rather than replaced.
- `ErrorCode` / `GlobalExceptionHandler` — every new failure routes through the
  existing contract.
- `TextNormalizer` — used for `normalized_title`, the same function companies
  and profiles use, so step 7 can join on it.
- `SecurityConfig` — default-deny; job reads were added to the public list and
  writes need no new configuration.
- Company search — the existing pattern (`LIKE`, capped page size, fixed sort)
  informed what to do differently here, since jobs need far more filters.

## Database changes

`V4__create_jobs_table.sql` creates `jobs`. `V5__add_job_search_indexes.sql`
adds the search index separately, because an applied migration is never edited,
only followed.

Choices worth recording:

- **`ON DELETE RESTRICT`, not CASCADE**, on the company foreign key. Companies
  have no delete endpoint, so a cascade would only ever fire by accident — and
  silently destroying every opening a company posted is not a side effect anyone
  wants from an administrative action.
- **`experience_min` / `experience_max` are nullable, and null means
  unspecified, not zero.** Plenty of real postings state no range.
- **`description` is `TEXT`** with the 20000-character cap in the API, which is
  where a limit on request size belongs.
- **No unique constraint on `(company_id, title)`.** The same company genuinely
  posts several openings with identical titles for different teams; a constraint
  that rejects real data is worse than a duplicate.
- CHECK constraints mirror both enums and the experience range, so invalid data
  cannot arrive by any route.

## Search and filter architecture

```
JobController            binds and validates parameters
      │                  builds Pageable via JobSortParser (allowlist)
      ▼
JobSearchCriteria        ten optional filters in one value; null = don't filter
      │                  validate() covers the cross-field rules
      ▼
JobSpecifications        criteria -> JPA predicates
      │
      ▼
JobRepository            JpaSpecificationExecutor + @EntityGraph
      │
      ▼
PostgreSQL               filters, sorts and pages
```

`Specification` was chosen because there are ten optional filters. The
alternative — one JPQL query repeating `(:param IS NULL OR column = :param)` ten
times — works but is unreadable at this size and makes PostgreSQL plan
conditions nobody asked for. It is also plain Spring Data: no query framework of
our own, each filter a five-line function.

The criteria record exists because a repository method with ten parameters
cannot be called correctly and grows another with every filter added.

## Supported query parameters

| Parameter | Type | Notes |
| --------- | ---- | ----- |
| `search` | string ≤ 200 | title OR description, case-insensitive substring |
| `companyId` | UUID | |
| `location` | string ≤ 200 | case-insensitive substring |
| `employmentType` | enum | 6 values; unknown is a 400 |
| `workMode` | enum | 3 values |
| `experienceMin` / `experienceMax` | 0–60 | range overlap |
| `active` | boolean | default `true`; anything else needs a token |
| `postedAfter` / `postedBefore` | ISO-8601 instant | inclusive |
| `page` / `size` | int | default 0 / 20, max size 50 |
| `sort` | string | `postedAt`, `createdAt`, `title` |

## Boolean filter behaviour

```
search AND companyId AND location AND employmentType AND workMode
       AND experience AND active AND postedAfter AND postedBefore

search itself:  title LIKE %term%  OR  description LIKE %term%
```

Each filter narrows the result, so they AND. The keyword ORs across two columns
because they are two places the same word might appear. The company name is
deliberately *not* searched: `search=google` should find jobs about Google, not
every opening at Google — a different question, answered by `companyId`.

**Experience is a range overlap**, with either end open on either side:

```
match iff  (jobMin ?? 0) ≤ (searchMax ?? ∞)  AND  (jobMax ?? ∞) ≥ (searchMin ?? 0)
```

Each predicate is written "unspecified OR within bound", so a job with no stated
experience is never excluded. Treating null as 0 would quietly drop those jobs
from every experience-filtered search — the class of bug where users decide
search is broken but cannot say why. There is a test named after exactly that.

**Case-insensitivity** is `LOWER(column) LIKE LOWER(pattern)` — portable,
obvious, and correct for `java`/`Java`/`JAVA`/`jAvA`.

**User wildcards are escaped.** `%` and `_` are `LIKE` metacharacters, so
searching `100%` would otherwise match everything and `_` would match every job.
Company search does not need this because normalization strips those characters;
job search matches raw text and must escape explicitly, with `'\\'` declared as
the escape character or the escaping does nothing.

## Pagination strategy

Spring Data `Pageable`, built in the controller rather than bound automatically.
Default page 0, size 20, **hard maximum 50** — without a ceiling
`?size=1000000` is a free denial-of-service against a 512 MB container.

`PageResponse` gained `hasNext` and `hasPrevious` alongside the existing
`first`/`last`. An empty result is a `200` with an empty array and
`totalElements: 0`; no list endpoint returns 404 because nothing matched.

## Sorting strategy

Allowlist of `postedAt`, `createdAt`, `title`. Binding `Pageable` directly would
let a caller sort by any mapped property — including unindexed ones — and turn a
typo into a `PropertyReferenceException` and a 500.

Default is `postedAt,desc`: a stale opening is worse than a less relevant one,
and there is no relevance score yet.

**`id` is always appended as a tiebreaker.** Without one, two jobs posted in the
same second can swap places between page 1 and page 2, so a row appears twice
and another is never seen. Invisible with tidy test data; obvious in production.

## Performance considerations

Nothing is filtered in Java. Predicates become SQL, `LIMIT`/`OFFSET` are applied
by PostgreSQL, and `totalElements` comes from a count query rather than the size
of a list we fetched.

**PostgreSQL is enough for V1.** A search engine is a second datastore to
deploy, keep in sync and pay for; it earns that cost at relevance ranking, fuzzy
matching or millions of documents. V1 needs substring matching over a few
thousand rows. Adding one now would also split the canonical data from what
search reads, with an indexing step that can fail. The honest limit is that
`develper` finds nothing — when that matters, PostgreSQL full-text search is the
next step and a separate engine the one after.

## Database indexes

| Index | Why |
| ----- | --- |
| `ix_jobs_company_id` (V4) | PostgreSQL does not index a foreign key automatically. |
| `ix_jobs_active_posted_at (active, posted_at DESC, id)` (V5) | Serves the default query — active jobs, newest first — so rows come back already ordered and the scan stops after one page. Equality column first, ordering column second. |

Deliberately not indexed, with the reasoning written into the migration:

- `employment_type`, `work_mode` — six and three values. An index whose entries
  each match a large slice of the table cannot narrow enough to beat a scan; the
  planner ignores it while writes still pay to maintain it.
- `title`, `description`, `location` — `LOWER(col) LIKE '%term%'` can use
  neither a leading wildcard nor a plain index on `LOWER(col)`. The real fix is
  `CREATE EXTENSION pg_trgm` plus a GIN index; not adopted because it needs an
  extension a managed free tier may not allow, costs real write time and disk,
  and at V1 volumes the scan is milliseconds. It is written down in V5 so the
  next person does not have to rediscover it.

## N+1 analysis

Every result row renders its company's name and slug, and `Job.company` is
`LAZY`. Left alone, one query for a page of 20 jobs is followed by up to 20
more — invisible on seed data, ruinous on a real list.

Fixed by re-declaring `findAll(Specification, Pageable)` on the repository with
`@EntityGraph(attributePaths = "company")`.

This is safe **specifically because the association is to-one.** Fetch joining a
*collection* alongside pagination forces Hibernate to load every row and
paginate in memory (the `HHH000104` warning). A to-one join multiplies no rows,
so `LIMIT`/`OFFSET` still work in SQL.

Rejected: making the association `EAGER`, which drags a company into every query
touching a job whether or not anyone wanted it; and a DTO projection, faster
still but a second mapping to keep in step with the entity.

The same `@EntityGraph` is used by `findWithCompanyById` for the detail
endpoint.

## Files created

```
backend/src/main/resources/db/migration/V4__create_jobs_table.sql
backend/src/main/resources/db/migration/V5__add_job_search_indexes.sql
backend/src/main/resources/db/seed/V9002__dev_seed_jobs.sql
backend/src/main/java/com/joblens/api/job/JobController.java
backend/src/main/java/com/joblens/api/job/JobService.java
backend/src/main/java/com/joblens/api/job/JobRepository.java
backend/src/main/java/com/joblens/api/job/JobSearchCriteria.java
backend/src/main/java/com/joblens/api/job/JobSpecifications.java
backend/src/main/java/com/joblens/api/job/JobSortParser.java
backend/src/main/java/com/joblens/api/job/domain/{Job,EmploymentType,WorkMode}.java
backend/src/main/java/com/joblens/api/job/dto/{CreateJobRequest,UpdateJobRequest,
    JobResponse,JobSummary,JobCompanyRef,JobUrlPatterns}.java
backend/src/main/java/com/joblens/api/job/exception/JobNotFoundException.java
backend/src/main/java/com/joblens/api/common/exception/InvalidRequestException.java
backend/src/test/java/com/joblens/api/job/JobSearchRepositoryTest.java
backend/src/test/java/com/joblens/api/job/JobControllerTest.java
backend/src/test/java/com/joblens/api/job/JobServiceTest.java
backend/src/test/java/com/joblens/api/job/JobSortParserTest.java
backend/src/test/java/com/joblens/api/job/JobSearchCriteriaTest.java
docs/api/jobs.md
docs/daily/day-06.md
```

## Files modified

```
backend/.../common/response/PageResponse.java   hasNext / hasPrevious
backend/.../common/exception/ErrorCode.java     JOB_NOT_FOUND
backend/.../config/SecurityConfig.java          GET /api/v1/jobs/** public
backend/.../security/CurrentUser.java           optional() for public endpoints
                                                that behave differently when
                                                signed in
backend/.../job/package-info.java               documents the populated module
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md, docs/VERIFICATION.md
```

## Tests created

| Class | Tier | Covers |
| ----- | ---- | ------ |
| `JobSearchRepositoryTest` | repository, real PostgreSQL | Keyword in title; keyword only in description; case-insensitivity across four spellings; partial words; wildcard escaping; empty result; company, location (three spellings of one city), employment type, work mode, active filters; closed jobs excluded by default and retrievable explicitly; experienceMin, experienceMax, a range, and the "unspecified experience is never excluded" case; open-ended job ranges; postedAfter, postedBefore, a window; all filters combined; contradictory filters; database-side paging; pages not repeating rows; default and explicit sorting; fetch by id with company; exists-by-company |
| `JobControllerTest` | `@WebMvcTest` | Pagination metadata including `hasNext`/`hasPrevious`; `active` defaulting to true; every filter binding into the criteria; empty result as 200; unknown enum, malformed UUID, malformed date, negative experience, oversized page, negative page, disallowed sort field; anonymous `active=false` rejected; 404 for missing job; create validation and 404 for unknown company; non-http apply URL |
| `JobServiceTest` | unit | Creation against an existing company with trimming and normalization; `postedAt` defaulting to now; unknown company rejected; not found; update recomputing the normalized title; update preserving `postedAt`; close hiding without deleting; criteria validated before querying |
| `JobSortParserTest` | unit | Default; blank; field and direction; tiebreaker always appended; per-field default direction; six rejected fields including a property that exists but is not allowlisted, and a SQL-injection-shaped string; unknown direction |
| `JobSearchCriteriaTest` | unit | Empty, ordered, equal and one-sided ranges accepted; inverted experience range and inverted date window rejected |

The repository fixtures are deliberately awkward — a keyword in a description
but not a title, three spellings of one city, jobs with no experience stated, a
closed job. Searching over tidy data proves very little.

## Commands for the separate test machine

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
curl -i 'http://localhost:8080/api/v1/jobs'
curl -i 'http://localhost:8080/api/v1/jobs?search=java'
curl -i 'http://localhost:8080/api/v1/jobs?search=JAVA'
curl -i 'http://localhost:8080/api/v1/jobs?location=pune'
curl -i 'http://localhost:8080/api/v1/jobs?workMode=REMOTE'
curl -i 'http://localhost:8080/api/v1/jobs?search=java&location=Pune&workMode=HYBRID'
curl -i 'http://localhost:8080/api/v1/jobs?experienceMin=2'
curl -i 'http://localhost:8080/api/v1/jobs?sort=title,asc&size=3'
```

```bash
# Rejections
curl -i 'http://localhost:8080/api/v1/jobs?employmentType=PERMANENT'
curl -i 'http://localhost:8080/api/v1/jobs?size=1000000'
curl -i 'http://localhost:8080/api/v1/jobs?sort=salary,desc'
curl -i 'http://localhost:8080/api/v1/jobs?experienceMin=5&experienceMax=2'
curl -i 'http://localhost:8080/api/v1/jobs?postedAfter=last%20tuesday'
curl -i 'http://localhost:8080/api/v1/jobs?active=false'
```

```sql
-- Confirm the search index is actually used
EXPLAIN ANALYZE
SELECT * FROM jobs WHERE active = true ORDER BY posted_at DESC, id LIMIT 20;
```

With `show-sql` on in the dev profile, watch the log during a search: it should
show **one** select for the page plus **one** count — not one per result row.
That is the N+1 check.

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `mvn`, `java`,
`javac`, `npm`, `node`, `docker`, `docker compose`, `python`, `pip`, `psql`,
`curl`. Nothing here has been compiled, started or tested. No claim is made that
the migrations apply, that any query runs, that the index is used, or that any
test passes.

## Things that could not be verified

- **That it compiles.** The least certain areas are the Criteria API usage in
  `JobSpecifications` — `root.get("company").get("id")`, the typed comparisons
  in the experience predicate, and the three-argument `like(..., '\\')` overload
  — and the `@EntityGraph` on a re-declared `JpaSpecificationExecutor.findAll`,
  which is a documented Spring Data feature but one I have not run here.
- **That `@EntityGraph` actually eliminates the N+1.** The mechanism is right;
  whether Hibernate applies the graph to a specification query in this version
  is exactly what the SQL log will show. This is the first thing to check.
- **That the composite index is chosen by the planner.** `EXPLAIN ANALYZE` above
  answers it. On a nearly empty table PostgreSQL will prefer a sequential scan
  regardless, which is correct behaviour and not a failure.
- **That `Instant` binds from a request parameter** with
  `@DateTimeFormat(iso = DATE_TIME)`. It should; a `400` on a valid timestamp
  would mean a converter is needed.
- **Whether an unparseable enum produces `MALFORMED_REQUEST` or something
  else.** The controller test asserts only the 400 status for that reason.
- **That the experience overlap SQL is right at the boundaries.** The tests
  encode the intended semantics, but they have not run.
- `V9002` seed data assumes the `V9001` company UUIDs exist; if company seeds
  were changed, the job seeds will fail their foreign key.
- Dependency versions remain unresolved against a registry, and no step has yet
  been confirmed green in CI.

## Next step

Step 7 — Matching: score a job against a user profile using the skills,
preferred roles, preferred locations, remote preference and experience already
stored, joining on the normalized columns both sides already carry. The output
should explain itself, not just produce a number.

Not started.
