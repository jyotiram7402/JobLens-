# Jobs API

| Method | Path | Auth | Purpose |
| ------ | ---- | ---- | ------- |
| `GET` | `/api/v1/jobs` | public | Search openings |
| `GET` | `/api/v1/jobs/{id}` | public | Fetch one |
| `POST` | `/api/v1/jobs` | required | Create |
| `PUT` | `/api/v1/jobs/{id}` | required | Replace editable fields |
| `POST` | `/api/v1/jobs/{id}/close` | required | Withdraw from search |

Reads are public — discovery is the product. Writes need a bearer token.

There is no `DELETE`: tracking and scan history will reference jobs, and a
filled position is a historical fact, not a mistake to erase. `close` sets
`active = false`.

---

## Search

```http
GET /api/v1/jobs?search=java&location=Pune&page=0&size=20
```

Every parameter is optional. A bare `GET /api/v1/jobs` returns the first page
of active jobs, newest first.

| Parameter | Type | Notes |
| --------- | ---- | ----- |
| `search` | string ≤ 200 | Matched against **title OR description**, case-insensitive substring |
| `companyId` | UUID | Restrict to one company |
| `location` | string ≤ 200 | Case-insensitive substring of the job's location text |
| `employmentType` | enum | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP`, `TEMPORARY`, `OTHER` |
| `workMode` | enum | `ONSITE`, `HYBRID`, `REMOTE` |
| `experienceMin` | 0–60 | See [Experience](#experience) |
| `experienceMax` | 0–60 | |
| `active` | boolean | Defaults to `true`. Anything else needs a token. |
| `postedAfter` | ISO-8601 instant | Inclusive, e.g. `2026-09-01T00:00:00Z` |
| `postedBefore` | ISO-8601 instant | Inclusive |
| `page` | int ≥ 0 | Default `0` |
| `size` | 1–50 | Default `20` |
| `sort` | string | `postedAt`, `createdAt` or `title`, optionally `,asc` / `,desc` |

### How filters combine

```
search AND companyId AND location AND employmentType AND workMode
       AND experience AND active AND postedAfter AND postedBefore

where search itself is:   title LIKE %term%  OR  description LIKE %term%
```

Each filter narrows the result, so they AND. The keyword ORs across two columns
because they are two places the same word might appear.

### Case and partial matching

Case-insensitive on both sides (`LOWER(column) LIKE LOWER(pattern)`), so `java`,
`Java`, `JAVA` and `jAvA` behave identically. Matching is substring, so
`search=spring` finds *Spring Boot Developer*, *Senior Spring Engineer* and
*Java Developer - Spring*.

`%` and `_` in your search term are escaped, not treated as wildcards —
searching `100%` looks for a literal percent sign.

There is no fuzzy matching, stemming or relevance ranking. `develper` finds
nothing.

### Experience

Both the job and the search describe a **range**, and a job matches when the two
**overlap**:

```
job range    = [ experienceMin ?? 0 , experienceMax ?? ∞ ]
search range = [ experienceMin ?? 0 , experienceMax ?? ∞ ]

match  iff  jobMin ≤ searchMax  AND  jobMax ≥ searchMin
```

- `experienceMin=2` — every job someone with 2 years could take. That includes
  a job asking for 5+, because you may have more than the minimum you stated.
- `experienceMax=4` — every job reachable with at most 4 years. A job demanding
  6–10 is excluded.
- `experienceMin=3&experienceMax=4` — jobs whose range overlaps 3–4.

**A job with no stated experience is never excluded.** Null means the employer
said nothing, not that they want zero years. Treating it as 0 would silently
drop those jobs from every experience-filtered search.

### Active

Defaults to `true`, because discovery means open positions.

`active=false` requires authentication and returns `401 UNAUTHENTICATED`
otherwise — listing withdrawn positions is an administrative view, and an
anonymous caller should not be able to enumerate jobs an employer deliberately
took down.

There is no "give me both" option in V1.

### Sorting

Default is `postedAt,desc` — newest first, since a stale opening is worse than
a less relevant one and there is no relevance score yet.

Only `postedAt`, `createdAt` and `title` are accepted; anything else is a `400`.
An open sort parameter would let a caller order by any mapped column, including
unindexed ones, and turn a typo into a 500.

`id` is always appended as a tiebreaker. Without one, two jobs posted in the
same second can swap places between pages — so one row appears twice and another
is never seen.

### Response

```json
{
  "content": [
    {
      "id": "0192f4b0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
      "title": "Java Backend Developer",
      "company": {
        "id": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
        "name": "Example Company",
        "slug": "example-company"
      },
      "location": "Pune, Maharashtra, India",
      "employmentType": "FULL_TIME",
      "workMode": "HYBRID",
      "experienceMin": 2,
      "experienceMax": 5,
      "postedAt": "2026-09-20T10:00:00Z",
      "active": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 125,
  "totalPages": 7,
  "first": true,
  "last": false,
  "hasNext": true,
  "hasPrevious": false
}
```

Results are summaries — no `description`, which on a page of 20 would be most of
the payload for text no list UI shows. Fetch the job by id for the rest.

**An empty result is a `200`**, never a `404`:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

"No jobs match these filters" is a successful answer to a well-formed question.

### More examples

```bash
curl '<api>/api/v1/jobs?search=spring&workMode=HYBRID&employmentType=FULL_TIME'
curl '<api>/api/v1/jobs?companyId=0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10'
curl '<api>/api/v1/jobs?experienceMin=2&experienceMax=5&location=Pune'
curl '<api>/api/v1/jobs?postedAfter=2026-09-01T00:00:00Z&sort=postedAt,asc'
curl '<api>/api/v1/jobs?search=java&location=Pune&workMode=HYBRID&page=1&size=10'
```

---

## Fetch one job

```http
GET /api/v1/jobs/{id}
```

Returns the full record, including `description` and `applyUrl`. `404` if it
does not exist, `400` if the id is not a UUID.

`normalizedTitle` and `version` are internal and never returned.

---

## Create

```http
POST /api/v1/jobs
Authorization: Bearer <token>
```

```json
{
  "companyId": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "title": "Java Backend Developer",
  "description": "Build and maintain REST services.",
  "location": "Pune, Maharashtra, India",
  "employmentType": "FULL_TIME",
  "workMode": "HYBRID",
  "experienceMin": 2,
  "experienceMax": 5,
  "applyUrl": "https://example.com/careers/123",
  "postedAt": "2026-09-20T10:00:00Z"
}
```

`201` with a `Location` header. `404 COMPANY_NOT_FOUND` if the company does not
exist.

`companyId` is accepted here — unlike a user profile, where accepting an owner
id would be the whole IDOR problem. A job genuinely belongs to a company the
caller chooses.

`postedAt` is optional and defaults to now. It is accepted because a job
imported from a careers page was posted when the employer posted it, not when
JobLens noticed — and the default sort depends on that being honest.

| Field | Rule |
| ----- | ---- |
| `companyId` | required, must exist |
| `title` | required, ≤ 200 characters |
| `description` | ≤ 20000 characters |
| `location` | ≤ 200 characters |
| `employmentType`, `workMode` | required, valid enum value |
| `experienceMin`, `experienceMax` | 0–60, min ≤ max |
| `applyUrl` | ≤ 1000 characters, `http`/`https` only |

The URL scheme restriction matters: without it a client could store
`javascript:...` and the frontend would render it as the Apply button.

---

## Update

```http
PUT /api/v1/jobs/{id}
```

Same body as create, minus `companyId`. Moving an opening between employers is
not an edit — it is a different job — and the column is mapped
`updatable = false`.

Full replacement: an omitted optional field is cleared. Omitting `postedAt`
keeps the existing value rather than resetting it to now.

---

## Close

```http
POST /api/v1/jobs/{id}/close
```

Sets `active = false`, so the job disappears from public search while everything
referencing it stays intact.

---

## Errors

| Code | Status | When |
| ---- | ------ | ---- |
| `VALIDATION_ERROR` | 400 | Bad parameter, inverted range, disallowed sort field |
| `MALFORMED_REQUEST` | 400 | Unparseable enum, UUID, date or JSON |
| `UNAUTHENTICATED` | 401 | Write without a token, or `active=false` anonymously |
| `JOB_NOT_FOUND` | 404 | No job with that id |
| `COMPANY_NOT_FOUND` | 404 | Creating against a company that does not exist |

Standard error body throughout, including a `traceId`.
