# Matching API

| Method | Path | Auth | Purpose |
| ------ | ---- | ---- | ------- |
| `GET` | `/api/v1/jobs/{jobId}/match` | required | How this job scores for you |
| `GET` | `/api/v1/jobs/recommended?page=&size=` | required | Recent openings, best match first |

Both require a bearer token, and **neither accepts a user id**. The profile used
is always the one the verified token points at, so there is nothing to tamper
with — the same structural defence the `/users/me` endpoints use.

Note that ordinary job reads (`GET /api/v1/jobs`, `GET /api/v1/jobs/{id}`) stay
public. These two are carved out of that rule.

---

## The score

Five criteria, weighted, out of 100:

| Criterion | Weight | Compares |
| --------- | -----: | -------- |
| Skills | 50 | Your skills against the skills the job asks for |
| Experience | 20 | Your years against the job's range |
| Location | 15 | Your preferred locations against the job's |
| Role | 10 | Your preferred job titles against the job's title |
| Work mode | 5 | Your remote preference against the job's work mode |

Skills are worth as much as the other four together because they are the only
criterion about *capability* — everything else is a preference or a
circumstance.

**The score is never just a number.** The breakdown and explanation are the
point: "88%" is not something you can act on, argue with or learn from.

### Missing data does not count against you

A criterion that cannot be judged is **excluded from the total, along with its
weight** — it does not score zero.

```
score = round(earned / available × 100)

where a criterion contributes to `available` only if there was
enough data on both sides to judge it.
```

So if you have not said how you want to work, work mode drops out and you are
scored out of 95 rather than losing 5 points for a question you did not answer.

The honest consequence: two scores are strictly comparable only when they were
computed over the same criteria. A 90 from skills alone is a thinner claim than
a 90 from all five, which is why every response reports which criteria applied
and the explanation says so in words.

If *nothing* can be judged, the response is `"scored": false` rather than a
score of 0 — "we cannot tell" and "terrible match" lead you to do completely
different things.

### The rules, exactly

**Skills** — the fraction of the job's skills you have. Job asks for 5, you have
4 → 0.8 → 40 of 50. Skills you have that the job does not ask for are neither
rewarded nor penalised. Not judged if either side has no skills recorded.

**Experience** — your years against the job's range, either end of which may be
open:

| Situation | Score |
| --------- | ----- |
| Inside the range | full |
| Short of the minimum | `1 − shortfall/3`, nothing at 3 years short |
| Above the maximum | `1 − 0.1 × excess`, never below half |

Deliberately asymmetric: being under-qualified is a real obstacle, while being
over-qualified does not stop you doing the work. Not judged if the job states no
range or you have not given your years.

**Location** — whole-word matching against the job's location text, so `Pune`
matches `Pune, Maharashtra, India` and `Hybrid - Pune`. Substring matching would
match a longer word that merely starts the same. Preferring `Remote` matches a
remote job whatever its address says. Binary — real distance is a later step.

**Role** — what fraction of the words in one of your preferred titles appears in
the job's title. `Java Backend Developer` against `Senior Java Backend
Developer` is 3/3; extra words in the title are free. Below half the overlap is
treated as coincidence and scores nothing, because nearly every engineering
title shares "developer" with nearly every other.

**Work mode**:

| | job REMOTE | job HYBRID | job ONSITE |
|---|---|---|---|
| **REMOTE** | 1.0 | 0.5 | 0.0 |
| **HYBRID** | 0.5 | 1.0 | 0.5 |
| **ONSITE** | 0.0 | 0.5 | 1.0 |

Hybrid is the middle ground in both directions. A preference of `ANY` means you
have not expressed one, so it is not judged — awarding full marks for
indifference would advantage people who skipped the question.

### No AI

Every rule above is arithmetic you can read and disagree with. A model would
probably rank better on average and could not tell you why, and "why did this
score 72?" is a question JobLens has to answer. AI-assisted matching comes
later and will sit beside this, not replace it.

---

## Match one job

```http
GET /api/v1/jobs/{jobId}/match
Authorization: Bearer <token>
```

```json
{
  "jobId": "0192f4b0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "scored": true,
  "score": 88,
  "maxScore": 100,
  "breakdown": {
    "skills": {
      "score": 38,
      "maxScore": 50,
      "applicable": true,
      "matched": true,
      "reason": "You have 3 of the 4 skills this job asks for.",
      "matchedSkills": ["Java", "Spring Boot", "Docker"],
      "missingSkills": ["PostgreSQL"]
    },
    "experience": {
      "score": 20, "maxScore": 20, "applicable": true, "matched": true,
      "reason": "Your 3 years of experience fit what this job is asking for."
    },
    "location": {
      "score": 15, "maxScore": 15, "applicable": true, "matched": true,
      "reason": "This job is in a location you are looking for."
    },
    "role": {
      "score": 10, "maxScore": 10, "applicable": true, "matched": true,
      "reason": "This job title matches a role you are looking for."
    },
    "workMode": {
      "score": 5, "maxScore": 5, "applicable": true, "matched": true,
      "reason": "This job's working arrangement is the one you prefer."
    }
  },
  "explanation": [
    "This job scores 88 out of 100, based on 5 of the 5 things JobLens compares.",
    "You have Java, Spring Boot and Docker, which this job asks for.",
    "Your 3 years of experience fit what this job is asking for.",
    "This job is in a location you are looking for.",
    "This job title matches a role you are looking for.",
    "This job's working arrangement is the one you prefer.",
    "The one skill gap is PostgreSQL."
  ]
}
```

A skipped criterion looks like this — `maxScore` is 0, which is what makes the
breakdown add up:

```json
"workMode": {
  "score": 0, "maxScore": 0, "applicable": false, "matched": false,
  "reason": "You have not said how you want to work, so the working arrangement could not be compared."
}
```

| Status | When |
| ------ | ---- |
| `200` | Scored, or `"scored": false` if nothing could be compared |
| `401` | No token, or a bad one |
| `404` | `JOB_NOT_FOUND` |
| `422` | `PROFILE_NOT_READY` — your profile has nothing to match on |

`422` rather than `400`: the request is perfectly well formed, and nothing about
it can be fixed. The profile has to be filled in, and the message says so.

---

## Recommended jobs

```http
GET /api/v1/jobs/recommended?page=0&size=20
Authorization: Bearer <token>
```

```json
{
  "content": [
    {
      "job": {
        "id": "0192f4b0-...",
        "title": "Java Backend Developer",
        "company": { "id": "...", "name": "Example Company", "slug": "example-company" },
        "location": "Pune, Maharashtra, India",
        "employmentType": "FULL_TIME",
        "workMode": "HYBRID",
        "experienceMin": 2,
        "experienceMax": 5,
        "postedAt": "2026-09-20T10:00:00Z",
        "active": true
      },
      "scored": true,
      "score": 88,
      "explanation": [
        "This job scores 88 out of 100, based on 5 of the 5 things JobLens compares.",
        "You have Java, Spring Boot and Docker, which this job asks for."
      ]
    }
  ],
  "page": 0, "size": 20, "totalElements": 37, "totalPages": 2,
  "first": true, "last": false, "hasNext": true, "hasPrevious": false
}
```

Sorted by score descending, then by posting date descending, then by id. The
tiebreakers matter: without them, jobs on the same score come back in whatever
order the database produced and a client paging through can see one twice.

Each row carries only the first two sentences of the explanation. Call
`/jobs/{id}/match` for the full breakdown when a user opens one.

Page size defaults to 20 and is capped at 50.

### What this does and does not do

Recommendations score **the 200 most recent active jobs**, not the whole table.
That keeps one request to one page-sized query plus one skills query, whatever
the table grows to.

The limitation, stated plainly: **a strong match posted long enough ago to fall
outside that window will not be found.** `totalElements` is the number of
candidates considered, not the number of jobs in existence.

Fixing it properly means narrowing candidates by something better than recency —
jobs sharing at least one of your skills, which the `job_skills` index already
supports — or precomputing scores. Both are worth doing when there are enough
jobs for the window to bite. Neither is worth doing now, and neither needs Redis
or a recommendation service.

---

## No labels

JobLens reports the number and the reasons. It does not call a job a "Strong
Match" or a "Best Match", because those are product judgements dressed up as
facts — an 88 that suits you badly is still an 88, and a label would tell you
otherwise. If labels are added later they will be defined score ranges,
documented as product labels rather than objective assessments.
