# Day 07 - The job matching engine

**Date:** 2026-09-28
**Roadmap step:** 7 - Matching

## Objective

Score a job against a user's career profile, deterministically, and explain the
result in words. No AI.

## Existing implementation reviewed

- `UserProfile` already had skills, years of experience, preferred roles,
  preferred locations and a remote preference — all five inputs the matching
  model needs.
- `Job` had a title, location, work mode and an experience range, but **no
  structured skills**. That was the gap this step had to fill first.
- Profile skills were a private value collection (`user_skills`: profile_id,
  name, normalized_name), right when nothing else had skills.
- `TextNormalizer` in `common` was already shared by companies, job titles and
  profile preferences — the normalization matching needed already existed.

## Database changes

`V6__create_skills_tables.sql`. V1–V5 untouched.

```
skills               id, name, normalized_name (unique), audit columns
job_skills           PK (job_id, skill_id)
user_profile_skills  PK (profile_id, skill_id)
```

Plus a backfill: distinct skills lifted out of `user_skills` into `skills`,
re-linked through `user_profile_skills`, and the old table dropped.
`gen_random_uuid()` is core PostgreSQL from 13, so no extension is needed;
those rows get random v4 UUIDs rather than the application's time-ordered ones,
which is fine for a one-off backfill.

**Why a shared table rather than mirroring the existing pattern.** A private
`job_skills(job_id, name, normalized_name)` would have been less work and would
have matched what profiles already did. But matching has to ask whether a job's
"Java" and a user's "Java" are the same thing, and two independent text columns
can only be compared by string equality and will drift apart forever. A shared
row is an identity. It also makes "how many openings ask for Docker" answerable,
which step 13 will want.

Preferred roles and locations stayed as value collections. The difference is the
point: a skill is something jobs also reference, so it needs identity; a
preferred role is one person's wording of a preference with no counterpart table.

`ON DELETE RESTRICT` on both `skill_id` columns — a skill is referenced by many
rows, and deleting one should fail loudly rather than silently emptying part of
somebody's profile.

## Matching architecture

```
          User Profile
                │
   ┌────────────┼────────────┬───────────┬────────────┐
 Skills    Experience     Roles     Locations    Work mode
   │            │            │           │            │
   └────────────┴─────┬──────┴───────────┴────────────┘
                      ▼
              Matching Engine          (pure, no database)
                      │
                      ▼
                    Job
                      │
                      ▼
            Score + Breakdown + Explanation
```

`JobMatchingEngine` holds the rules and touches no database, clock or
randomness; `JobMatchingService` touches the database and holds no rules. The
engine takes `MatchProfile` and `MatchJob` value objects rather than entities,
which is what lets the rules be tested in milliseconds and reviewed without
reading a persistence layer.

## Scoring formula

```
for each of the five criteria:
    if there is enough data on both sides:
        earned    += fraction(0..1) × weight
        available += weight

score = round(earned / available × 100)
```

## Weight decisions

| Criterion | Weight | Why |
| --------- | -----: | --- |
| Skills | 50 | The only criterion about capability. Everything else is a preference or a circumstance, so skills are worth as much as the other four together. |
| Experience | 20 | A real signal but a crude one — two years somewhere demanding beats five somewhere idle — so it must not outweigh skills. |
| Location | 15 | A hard blocker in practice when wrong, but about circumstances rather than suitability. |
| Role | 10 | Titles are inconsistent between companies and a good match can be called almost anything. Low weight reflects low trust in the signal. |
| Work mode | 5 | Real but narrow: three values, and hybrid is partly compatible with both others. |

All five live in `MatchingWeights`, bound from `joblens.matching.weights.*` and
overridable per environment. The total is not enforced to be 100 — scores are
normalized against what could be evaluated, so the weights only need to be
sensible relative to each other.

## Skill matching

Fraction of the job's skills the user holds: 4 of 5 → 0.8 → 40 of 50.

Comparison is on the shared row, so casing and spacing differences are already
resolved by the time the engine sees them. Skills the user has that the job does
not ask for are neither rewarded nor penalised — knowing Kubernetes does not make
you a better fit for a job that never mentions it.

The explanation uses the *user's* spelling of a matched skill and the *job's*
spelling of a missing one, so each side reads as it was written.

Not judged if either side has no skills. A job with no structured skills is
common — nothing extracts them from descriptions yet — and awarding a perfect
score for absent data would make every under-described job look ideal.

## Experience strategy

| Situation | Score |
| --------- | ----- |
| Inside the range | 1.0 |
| Short of the minimum | `1 − shortfall/3`, reaching 0 at three years short |
| Above the maximum | `1 − 0.1 × excess`, floored at 0.5 |

Deliberately asymmetric. Being under-qualified is a real obstacle and closes
quickly. Being over-qualified does not stop you doing the work — it is a risk
that the employer passes or the salary does not fit — so it never falls below
half marks however senior the candidate.

Either end of the job's range may be open and is handled. Not judged if the job
states no range at all, or the user has not given their years: an unstated range
is not "any experience welcome".

## Location strategy

Whole-word matching against the job's location text. `pune` matches
`pune maharashtra india` and `hybrid pune`, but not `punegar`. Substring
matching is what produces the classic false positive where a short city name
matches half the table.

One special case: preferring "remote" matches a remote job whatever its address
says, because a remote job's location is usually an office that is beside the
point.

Binary. Real geographic distance needs coordinates and is a later step. Not
judged if either side is silent.

## Role strategy

What fraction of the words in a preferred title appear in the job's title, best
of several preferences. One direction only: extra words in the *title* are free,
which is what lets "Senior", a level suffix and a team name not spoil a match.

Below 0.5 the overlap is treated as coincidence and scores nothing — nearly
every engineering title shares "developer" or "engineer" with nearly every
other, and without a floor role scoring is noise.

## Work-mode strategy

| | job REMOTE | job HYBRID | job ONSITE |
|---|---|---|---|
| REMOTE | 1.0 | 0.5 | 0.0 |
| HYBRID | 0.5 | 1.0 | 0.5 |
| ONSITE | 0.0 | 0.5 | 1.0 |

Hybrid is the middle ground in both directions, so every pairing involving it
scores half rather than zero. Remote against onsite is the only genuine
contradiction.

`ANY` means the user has not expressed a preference, so it is not judged.
Awarding full marks for indifference would quietly advantage users who skipped
the question over users who answered it.

## Missing-data strategy

**A criterion that cannot be judged is excluded from the total and takes its
weight with it.** It does not score zero.

Collapsing "no data" into "no match" is the easiest way to make a matching
engine feel broken: a user who has not listed preferred locations has not failed
a location test, they have not taken one. Scoring them zero would punish an
incomplete profile and make every score incomparable with every other.

Worked example — everything matches except that the user gave no work-mode
preference and no preferred role:

```
applicable: skills 50, experience 20, location 15   -> available 85
earned:     50 + 20 + 15                            -> 85
score:      85 / 85 × 100                           -> 100
```

And a partial one:

```
job asks for Java + Rust, user has Java             -> 0.5 × 50 = 25
user has 3 years, job wants 2-5                     -> 1.0 × 20 = 20
no location on the job, no preferred role, ANY mode -> not judged
score: 45 / 70 × 100 = 64.28...                     -> 64
```

The cost, stated in the response and the docs: two scores are strictly
comparable only when computed over the same criteria. Every response reports
which applied, and the explanation's first sentence says "based on N of the 5
things JobLens compares".

If *nothing* can be judged, the result is `scored: false` rather than 0. Zero
reads as "terrible match" when it means "we cannot tell", and the two lead a
user to do completely different things. A profile with nothing in it at all gets
`422 PROFILE_NOT_READY` with a message naming what to add.

## Explanation

Generated from the outcome by `MatchExplanationWriter` — templates, not a
language model. Auditable, instant, free, and unable to hallucinate a
qualification the user never claimed, which in a product about job applications
is not a small property.

Order is score, then strengths, then gaps, then what could not be judged. The
matched and missing lists come from the same comparison the score did, so the
prose cannot contradict the breakdown. Long skill lists are truncated with "and
N more"; the full lists are in the structured breakdown.

## Recommendation approach

`GET /jobs/recommended` scores the 200 most recent active jobs, sorts by score
descending then posting date then id, and pages in memory.

One page-sized query plus one skills query, whatever the table grows to. Paging
is in memory because the ordering is per-user and cannot be expressed as SQL
ordering without storing scores; the candidate cap is what keeps that honest.

**Limitation, plainly:** a strong match posted outside that window will not be
found, and `totalElements` counts candidates rather than all jobs. The fix is to
narrow candidates by shared skills instead of recency — `ix_job_skills_skill_id`
already supports it — or to precompute scores. Worth doing when there are enough
jobs for the window to bite; neither needs Redis or a recommendation service.

## Performance

Two N+1 problems existed and both are handled:

- **Company per job** — the candidate query uses `findByActiveTrue` with
  `@EntityGraph(attributePaths = "company")`. Safe because the association is
  to-one; a collection fetch join alongside pagination would force in-memory
  paging.
- **Skills per job** — `findSkillsForJobs` returns every (job, skill) pair for
  the candidate set in one flat projection, grouped by job id in memory.
  Touching `job.getSkills()` inside the scoring loop would have been 200
  queries.

A single match uses `findWithCompanyAndSkillsById`: one to-one join plus one
collection, in one query.

## Files created

```
backend/src/main/resources/db/migration/V6__create_skills_tables.sql
backend/src/main/resources/db/seed/V9003__dev_seed_skills.sql
backend/src/main/java/com/joblens/api/skill/{SkillService,SkillRepository,package-info}.java
backend/src/main/java/com/joblens/api/skill/domain/Skill.java
backend/src/main/java/com/joblens/api/matching/MatchController.java
backend/src/main/java/com/joblens/api/matching/JobMatchingService.java
backend/src/main/java/com/joblens/api/matching/JobMatchingEngine.java
backend/src/main/java/com/joblens/api/matching/MatchExplanationWriter.java
backend/src/main/java/com/joblens/api/matching/MatchingWeights.java
backend/src/main/java/com/joblens/api/matching/MatchCriterion.java
backend/src/main/java/com/joblens/api/matching/{MatchProfile,MatchJob}.java
backend/src/main/java/com/joblens/api/matching/{CriterionOutcome,MatchOutcome}.java
backend/src/main/java/com/joblens/api/matching/dto/{MatchResponse,CriterionScore,RecommendedJob}.java
backend/src/main/java/com/joblens/api/matching/exception/ProfileNotReadyException.java
backend/src/main/java/com/joblens/api/matching/package-info.java
backend/src/test/java/com/joblens/api/matching/JobMatchingEngineTest.java
backend/src/test/java/com/joblens/api/matching/MatchExplanationWriterTest.java
backend/src/test/java/com/joblens/api/matching/MatchingSecurityIntegrationTest.java
docs/api/matching.md
docs/daily/day-07.md
```

## Files modified

```
backend/.../user/domain/UserProfile.java     skills -> ManyToMany Skill
backend/.../user/UserService.java            resolves skill names via SkillService
backend/.../job/domain/Job.java              skills association + replaceSkills
backend/.../job/JobService.java              resolves and stores job skills
backend/.../job/JobRepository.java           findByActiveTrue, findSkillsForJobs,
                                             findWithCompanyAndSkillsById
backend/.../job/dto/{Create,Update}JobRequest.java   skills field
backend/.../job/dto/JobResponse.java         skills field
backend/.../common/exception/ErrorCode.java  PROFILE_NOT_READY
backend/.../config/SecurityConfig.java       matching routes before the public
                                             jobs rule
backend/src/main/resources/application.yml   joblens.matching.weights.*
backend/src/test/.../job/{JobServiceTest,JobControllerTest}.java  new signatures
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md, docs/VERIFICATION.md
```

## Tests created

| Class | Covers |
| ----- | ------ |
| `JobMatchingEngineTest` | Score always 0–100; perfect match is 100; nothing matching is 0. **Skills:** full, partial, none, matched/missing lists, extra skills ignored, casing, job with none, user with none. **Experience:** inside the range, open-ended bounds, four points below the minimum, four above the maximum including the floor, zero experience, null range, null years. **Location:** exact, word inside a longer string, multi-word place, any of several, the `punegar` false-positive guard, no match, remote special case, missing on either side. **Role:** identical, extra title words, partial above the floor, weak overlap rejected, none, best of several, no preference. **Work mode:** all nine pairings plus `ANY`. **Normalization:** criteria excluded rather than zeroed, the 45/70 → 64 case, nothing judgeable → unscored. **Weights:** configurable and they change the score; defaults sum to 100. |
| `MatchExplanationWriterTest` | Names matched skills; **never claims a missing skill**; lists several gaps; says nothing about matches when there are none; truncates long lists; reports score and how much was compared; strengths before gaps; explains skipped criteria; unscored result says what to do and reports no number. |
| `MatchingSecurityIntegrationTest` | Match and recommendations both reject anonymous callers; public job reads still public; malformed token rejected; a `userId` parameter changes nothing; full breakdown and explanation end to end; skill names match across casing through the shared table; empty profile gets 422 rather than 0; unknown job 404; recommendations paginate; page size capped. |

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
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"SecurePassword123!"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
```

```bash
curl -s -X PUT http://localhost:8080/api/v1/users/me/profile \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"skills":["Java","Spring Boot","Docker"],"yearsOfExperience":3,
       "preferredRoles":["Java Backend Developer"],"preferredLocations":["Pune"],
       "remotePreference":"HYBRID"}'
```

```bash
curl -s "http://localhost:8080/api/v1/jobs/recommended" -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/v1/jobs/<jobId>/match" -H "Authorization: Bearer $TOKEN"
```

```bash
# Must be 401, not 200 -- the security rule ordering
curl -i "http://localhost:8080/api/v1/jobs/recommended"
curl -i "http://localhost:8080/api/v1/jobs/<jobId>/match"
# Must still be 200 -- ordinary discovery unaffected
curl -i "http://localhost:8080/api/v1/jobs"
```

```sql
-- The backfill did what it claims
SELECT count(*) FROM skills;
SELECT count(*) FROM user_profile_skills;
SELECT to_regclass('user_skills');   -- expect NULL: the old table is gone
```

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `mvn`, `java`,
`javac`, `npm`, `node`, `docker`, `docker compose`, `python`, `pip`, `psql`,
`curl`. Nothing here has been compiled, started or tested. No claim is made that
the migration applies, that the backfill preserves data, that any endpoint
responds, or that any test passes.

## Things that could not be verified

- **That it compiles.** Least certain: the `JobSkillRow` interface projection
  on a JPQL query with aliases, the `@ManyToMany` mappings against the join
  tables in V6, and `@EnableConfigurationProperties(MatchingWeights.class)` on a
  `@Component` rather than a `@Configuration`.
- **That the V6 backfill is correct.** It rewrites existing profile data and
  drops a table. The SQL is straightforward, but it has never run, and this is
  the only destructive migration in the project so far. The `to_regclass` check
  above is the confirmation.
- **That `ddl-auto: validate` accepts the new mappings** — a mismatch between
  the `@JoinTable` columns and V6 fails startup, which is the intended safety
  net but has not been exercised.
- **That `GET /jobs/recommended` is not shadowed by `GET /jobs/{id}`.** Spring
  should prefer the literal path over the variable one, but if `recommended` is
  parsed as a UUID the endpoint returns 400 instead. Worth checking first.
- **That the security rule ordering works as reasoned.** It is the most
  consequential detail in this step — get it wrong and match results become an
  anonymous read. There is a test, but the test has not run.
- **That the experience arithmetic matches the table** at every boundary; the
  parameterised tests encode the intent but have not executed.
- Steps 1–6 remain unverified: nothing has yet been through CI or a deployment.

## Next step

Step 8 — Frontend: login and registration, company and job browsing with the
search filters, the match score and explanation on a job, and profile editing.

Not started.
