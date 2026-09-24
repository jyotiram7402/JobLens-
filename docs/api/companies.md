# Companies API

Base path `/api/v1/companies`. No authentication yet — that arrives in step 4,
after which creating and updating a company will require it.

All timestamps are UTC ISO-8601. All ids are UUIDs.

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `POST` | `/api/v1/companies` | Create a company |
| `GET` | `/api/v1/companies?search=&page=&size=` | Search active companies |
| `GET` | `/api/v1/companies/{id}` | Fetch one company by id |
| `GET` | `/api/v1/companies/by-slug/{slug}` | Fetch one company by URL slug |
| `PUT` | `/api/v1/companies/{id}` | Replace a company's editable fields |

There is no `DELETE`. See [Deletion](#deletion).

---

## Create a company

```http
POST /api/v1/companies
Content-Type: application/json
```

```json
{
  "name": "Tata Consultancy Services",
  "description": "Information technology services company",
  "websiteUrl": "https://example.com",
  "careersUrl": "https://example.com/careers",
  "logoUrl": "https://example.com/logo.png",
  "industry": "Information Technology",
  "location": "Pune, India"
}
```

`201 Created`, with a `Location` header pointing at the new resource:

```json
{
  "id": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "slug": "tata-consultancy-services",
  "name": "Tata Consultancy Services",
  "description": "Information technology services company",
  "websiteUrl": "https://example.com",
  "careersUrl": "https://example.com/careers",
  "logoUrl": "https://example.com/logo.png",
  "industry": "Information Technology",
  "location": "Pune, India",
  "active": true,
  "createdAt": "2026-09-24T10:15:30Z",
  "updatedAt": "2026-09-24T10:15:30Z"
}
```

Only `name` is required. `id`, `slug`, `active` and the timestamps are
system-managed: there is no field to supply them in, and sending one is a
`400`, not a silent overwrite.

### Validation

| Field | Rule |
| ----- | ---- |
| `name` | required, not blank, at most 200 characters, must contain at least one letter or digit |
| `description` | at most 2000 characters |
| `websiteUrl`, `careersUrl`, `logoUrl` | at most 500 characters, and `http`/`https` only |
| `industry` | at most 120 characters |
| `location` | at most 200 characters |

The URL scheme restriction matters: without it a client could store
`javascript:...` and the frontend would render it as a link.

`400 Bad Request` reports every failing field at once:

```json
{
  "timestamp": "2026-09-24T10:15:30Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/companies",
  "traceId": "6f1c2b9e4a7d4c31",
  "details": {
    "name": "must not be blank",
    "websiteUrl": "must be an http or https URL"
  }
}
```

### Duplicates

`409 Conflict` when a company with the same normalized name already exists —
so `TATA CONSULTANCY SERVICES` will not create a second record alongside
`Tata Consultancy Services`:

```json
{
  "timestamp": "2026-09-24T10:15:30Z",
  "status": 409,
  "error": "COMPANY_ALREADY_EXISTS",
  "message": "A company matching 'TATA CONSULTANCY SERVICES' already exists: 'tata-consultancy-services'",
  "path": "/api/v1/companies",
  "traceId": "6f1c2b9e4a7d4c31"
}
```

The message names the existing slug so the client can link to it rather than
guess what it collided with.

---

## Search companies

```http
GET /api/v1/companies?search=tata&page=0&size=20
```

| Parameter | Default | Notes |
| --------- | ------- | ----- |
| `search` | none | Matched against the normalized name, so case, spacing, accents and punctuation are all ignored. Omitted or blank returns everything active. |
| `page` | `0` | Zero-based. |
| `size` | `20` | Maximum `50`; above that is a `400`. |

Results are active companies only, ordered by name. Sorting is not
client-configurable yet.

`200 OK`:

```json
{
  "content": [
    {
      "id": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
      "slug": "tata-consultancy-services",
      "name": "Tata Consultancy Services",
      "industry": "Information Technology",
      "location": "Pune, India",
      "logoUrl": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Search results are summaries, not full records — no `description`, which on a
page of 20 would be most of the payload for text no list UI shows. Fetch the
company by id or slug for the rest.

---

## Fetch a company

```http
GET /api/v1/companies/0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10
GET /api/v1/companies/by-slug/tata-consultancy-services
```

`200 OK` returns the same body as create. `404 Not Found` otherwise:

```json
{
  "timestamp": "2026-09-24T10:15:30Z",
  "status": 404,
  "error": "COMPANY_NOT_FOUND",
  "message": "No company found with id 0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "path": "/api/v1/companies/0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "traceId": "6f1c2b9e4a7d4c31"
}
```

An id that is not a valid UUID is a `400`, not a `404`.

Two identifiers exist on purpose. The UUID is the stable primary key and what
other resources reference. The slug is the readable one, for URLs such as
`/companies/tata-consultancy-services`.

---

## Update a company

```http
PUT /api/v1/companies/{id}
```

Same body as create. This is a full replacement of the editable fields: an
omitted optional field is cleared, not left alone. (Partial updates would be
`PATCH`, which needs a way to tell "absent" from "set to null" — worth adding
when something actually needs it.)

Renaming recomputes the normalized name, so an update can return `409` if the
new name collides with a different company.

**The slug never changes.** It is a public identifier that already exists in
links, bookmarks and anything the frontend cached; regenerating it on every
rename would silently break all of them. A company renamed from `Acme` to
`Acme Global` keeps the slug `acme`.

---

## Deletion

Not implemented, deliberately.

Jobs, tracking rows and scan history will all reference a company. Deleting one
would either orphan those rows or cascade into deleting a user's saved data as
a side effect of an administrative action — and a scan that resolved to a
company is a historical fact that stays true even after the company is no longer
interesting.

The `active` flag already covers the real need: a company can be hidden from
search while everything pointing at it stays intact. A `PATCH /active` endpoint
is the natural next step once there is an authenticated user allowed to press
it. If genuine deletion is ever required, it should be a soft delete with a
`deleted_at` column and an explicit retention policy, not a `DELETE` route added
because REST has the verb.

---

## Status codes

| Code | When |
| ---- | ---- |
| `200 OK` | Successful fetch, search or update |
| `201 Created` | Company created, with a `Location` header |
| `400 Bad Request` | Validation failure, malformed JSON, or a malformed UUID |
| `404 Not Found` | No company with that id or slug |
| `409 Conflict` | A company with the same normalized name exists |
| `500 Internal Server Error` | A defect. The response carries a `traceId` and nothing else. |

Every error body has the same shape, always including `traceId` — which also
comes back on the `X-Correlation-Id` header and appears on every log line for
that request.
