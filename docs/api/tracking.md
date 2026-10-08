# Company Tracking API

Follow companies you are interested in. Every endpoint requires a bearer token,
and **none accepts a user id** — the backend always acts for the user the token
belongs to.

| Method | Path | Purpose | Success |
| ------ | ---- | ------- | ------- |
| `POST` | `/api/v1/companies/{companyId}/track` | Start tracking | `200` + status |
| `DELETE` | `/api/v1/companies/{companyId}/track` | Stop tracking | `204` |
| `GET` | `/api/v1/companies/{companyId}/track` | Is it tracked? | `200` + status |
| `GET` | `/api/v1/users/me/tracked-companies` | Your tracked companies | `200` + page |

Tracking *a company* is an action on that company, so it lives under
`/companies/{id}`. The list belongs to you, so it lives under `/users/me` with the
rest of what is yours.

---

## Track

```http
POST /api/v1/companies/{companyId}/track
Authorization: Bearer <token>
```

```json
{
  "companyId": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "tracked": true,
  "trackedAt": "2026-10-08T10:15:30Z"
}
```

**Idempotent.** Tracking a company you already track returns `200` with the
existing relationship — the original `trackedAt`, not a new one. It is not a
`409`: you asked for a state, the state holds, and a conflict would be an error
message describing a success.

`200` rather than `201` for the same reason, and because there is no separate
resource for a `Location` header to point at — the relationship is addressed by
company.

The response is the same shape `GET` returns, so the client can update its state
from it without asking again.

### Two requests at once

A double-click, or a retry after a slow response, can send two track requests
that both find "not tracked" and both try to insert. The database's unique
constraint on `(user_id, company_id)` lets exactly one succeed. The other reads
the winner's row and returns it, so **both requests get `200` and there is still
one row**.

---

## Untrack

```http
DELETE /api/v1/companies/{companyId}/track
```

`204 No Content` **whether or not the company was tracked** — the state you asked
for, not tracked, holds either way.

Removes the relationship only. The company itself is never touched.

---

## Status

```http
GET /api/v1/companies/{companyId}/track
```

```json
{ "companyId": "0192f4a0-...", "tracked": false, "trackedAt": null }
```

### Why a separate endpoint, not a field on the company

`GET /companies/{id}` is public and returns the same body to everyone. Adding a
`tracked` field would make one URL return different bodies to different callers,
couple the company module to tracking, and cost a tracking query on every
anonymous company view. A dedicated endpoint costs one small request, made only
when someone is signed in.

---

## List

```http
GET /api/v1/users/me/tracked-companies?page=0&size=12
```

```json
{
  "content": [
    {
      "companyId": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
      "name": "Example Company",
      "slug": "example-company",
      "logoUrl": null,
      "industry": "Information Technology",
      "location": "Pune, India",
      "websiteUrl": "https://example.com",
      "careersUrl": "https://example.com/careers",
      "trackedAt": "2026-10-08T10:15:30Z"
    }
  ],
  "page": 0, "size": 12, "totalElements": 1, "totalPages": 1,
  "first": true, "last": true, "hasNext": false, "hasPrevious": false
}
```

Most recently tracked first, with an id tiebreaker so two companies tracked in
the same instant cannot swap places between pages. Page size defaults to 12 and
is capped at 50.

Each row carries a card's worth of company information plus `trackedAt` — not the
full company record, and not the tracking row's own id, which nothing needs.

There is no `userId` parameter. Supplying one changes nothing.

---

## Errors

| Status | Code | When |
| ------ | ---- | ---- |
| `400` | `MALFORMED_REQUEST` | The company id is not a UUID |
| `400` | `VALIDATION_ERROR` | `page` negative or `size` above 50 |
| `401` | `UNAUTHENTICATED` / `TOKEN_*` | No token, or a bad one |
| `404` | `COMPANY_NOT_FOUND` | No such company — for all three per-company operations |

No new error codes: "already tracked" and "not tracked" are not errors, and a bad
company id is the same `COMPANY_NOT_FOUND` the company endpoints already return.

---

## Security notes

`GET /api/v1/companies/**` is public for discovery, so the status endpoint — a
`GET` under that prefix — is carved out **ahead** of that rule in
`SecurityConfig`. Rules are evaluated in order; listed afterwards, the public
rule would match first. `POST` and `DELETE` are not `GET`s and fall through to
the authenticated default.

That carve-out has one side effect worth knowing: the URL
`/companies/by-slug/track` has the same shape as the tracking route, so it too
requires a token. To stop that locking anonymous visitors out of a real company
page, **`track` is a reserved slug** — a company called "Track" is given
`track-2`.
