# Authentication and Users API

Two public endpoints to obtain a token, and three authenticated endpoints for
the signed-in user's own data.

| Method | Path | Auth | Purpose |
| ------ | ---- | ---- | ------- |
| `POST` | `/api/v1/auth/register` | public | Create an account |
| `POST` | `/api/v1/auth/login` | public | Exchange credentials for a token |
| `GET` | `/api/v1/users/me` | required | Account + profile |
| `GET` | `/api/v1/users/me/profile` | required | Profile only |
| `PUT` | `/api/v1/users/me/profile` | required | Replace the profile |

Authenticated requests carry the token in a header:

```
Authorization: Bearer <accessToken>
```

---

## Register

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

`201 Created`:

```json
{
  "user": {
    "id": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe"
  },
  "message": "Registration successful"
}
```

**Registration does not log you in.** There is no token in this response; call
`/auth/login` next. Two reasons: one way to obtain a token means one code path
to audit, and email verification — the obvious next requirement — belongs
exactly where auto-login would be. Not issuing a token now means adding
verification later does not have to take one away, which would break the
frontend.

An empty career profile is created with the account, so `/users/me/profile`
works immediately.

| Field | Rule |
| ----- | ---- |
| `email` | required, valid address, ≤ 254 characters. Stored lowercased. |
| `password` | required, 10–72 characters |
| `firstName`, `lastName` | required, ≤ 100 characters |

The password rules are length-based rather than "one capital, one symbol",
following current NIST guidance — composition rules mostly teach people to
write `Password1!`. The 72-character ceiling is not arbitrary: bcrypt silently
ignores everything past 72 **bytes**, so a longer password would be accepted
while part of it did nothing. The service also checks the UTF-8 byte length,
since a non-ASCII character costs more than one byte.

`409 Conflict` if the address already has an account:

```json
{
  "timestamp": "2026-09-24T10:15:30Z",
  "status": 409,
  "error": "EMAIL_ALREADY_REGISTERED",
  "message": "Email address is already registered",
  "path": "/api/v1/auth/register",
  "traceId": "6f1c2b9e4a7d4c31"
}
```

This does reveal that an address is registered, which is account enumeration.
It is an accepted trade-off: the alternative is to pretend to succeed and send
an email instead, and we have no email delivery yet. Login makes no such
concession.

---

## Login

```http
POST /api/v1/auth/login
```

```json
{ "email": "user@example.com", "password": "SecurePassword123!" }
```

`200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

`expiresIn` is seconds, so a client can warn or re-authenticate before the token
lapses instead of discovering expiry through a 401.

**Failures say as little as possible.** A wrong password and an unknown address
return the identical body:

```json
{
  "status": 401,
  "error": "INVALID_CREDENTIALS",
  "message": "Invalid email or password"
}
```

Distinguishing them would turn the login endpoint into a tool for discovering
which addresses have accounts. The service also spends the same time on both
cases — verifying against a dummy hash when no account exists — because an
answer that arrives in microseconds instead of ~100ms is just as revealing.

A disabled account returns `403 ACCOUNT_INACTIVE`, but **only after the password
has been verified**. Someone who already knows the password learns nothing new,
and someone guessing never reaches it.

---

## Current user

```http
GET /api/v1/users/me
Authorization: Bearer <token>
```

```json
{
  "id": "0192f4a0-3b1c-7c4e-9f2a-1b8d6e4c5a10",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "profile": {
    "headline": "Software Engineer",
    "summary": "Backend and full-stack engineering.",
    "yearsOfExperience": 2,
    "currentRole": "Software Engineer",
    "remotePreference": "HYBRID",
    "preferredRoles": ["Java Backend Developer", "Spring Boot Developer"],
    "preferredLocations": ["Pune", "Mumbai", "Remote"],
    "skills": ["Java", "Spring Boot", "React", "PostgreSQL"]
  }
}
```

Account and profile together, because that is what a client needs on load and
two round trips to a sleeping free-tier backend means two cold starts.

No password hash, no role, no `active` flag. Collections are always present and
empty rather than null.

---

## Profile

```http
GET  /api/v1/users/me/profile
PUT  /api/v1/users/me/profile
```

```json
{
  "headline": "Software Engineer",
  "summary": "Software developer interested in backend and full-stack engineering.",
  "yearsOfExperience": 2,
  "currentRole": "Software Engineer",
  "preferredRoles": ["Java Backend Developer", "Spring Boot Developer", "Full Stack Developer"],
  "preferredLocations": ["Pune", "Mumbai", "Remote"],
  "remotePreference": "HYBRID",
  "skills": ["Java", "Spring Boot", "React", "PostgreSQL"]
}
```

**There is no `userId` field, and there will never be one.** The profile being
written is always the one belonging to the verified token. There is no
`/users/{id}` route either. Accessing another user's data is not guarded
against — it is designed out, because the only id in play never comes from the
client.

PUT is a full replacement: an omitted field is cleared and an omitted collection
empties. That is how a skill is removed, which is why there is no delete
endpoint.

Skills, roles and locations are deduplicated by their normalized form, using the
same rules the company domain uses. Sending `["Java", "java", "  JAVA  "]`
stores one skill.

| Field | Rule |
| ----- | ---- |
| `headline` | ≤ 200 characters |
| `summary` | ≤ 2000 characters |
| `yearsOfExperience` | 0–60 |
| `currentRole` | ≤ 150 characters |
| `remotePreference` | `REMOTE`, `HYBRID`, `ONSITE`, `ANY`. Null means `ANY`. |
| `preferredRoles` | ≤ 20 entries, each non-blank and ≤ 150 characters |
| `preferredLocations` | ≤ 20 entries, each non-blank and ≤ 150 characters |
| `skills` | ≤ 50 entries, each non-blank and ≤ 80 characters |

The collection limits are not decoration: without them one request could insert
an unbounded number of rows, which on a 0.5 GB free-tier database is a cheap way
to fill it.

---

## Authentication errors

All use the standard error body, with a `traceId` that also appears on the
`X-Correlation-Id` response header.

| Code | Status | Meaning |
| ---- | ------ | ------- |
| `UNAUTHENTICATED` | 401 | No token supplied for a protected route |
| `TOKEN_EXPIRED` | 401 | The token was genuine but has lapsed — log in again |
| `TOKEN_INVALID` | 401 | Bad signature, wrong issuer, or malformed |
| `ACCESS_DENIED` | 403 | Authenticated, but not allowed |
| `INVALID_CREDENTIALS` | 401 | Wrong email or password (never says which) |
| `ACCOUNT_INACTIVE` | 403 | Correct credentials, disabled account |
| `EMAIL_ALREADY_REGISTERED` | 409 | Registration against an existing address |
| `USER_NOT_FOUND` | 404 | Valid token for an account that no longer exists |

`TOKEN_EXPIRED` and `TOKEN_INVALID` are separate because a client should react
differently: expired means log in again, invalid means something is wrong with
the client.

---

## What is public

| Route | Access |
| ----- | ------ |
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | public |
| `GET /actuator/health` | public (the platform's health check) |
| `GET /api/v1/meta` | public |
| `GET /api/v1/companies/**` | public — discovery is the product |
| `POST`/`PUT /api/v1/companies/**` | **authenticated** (tightened in this step) |
| everything else | authenticated by default |

The default is deny. A new endpoint is protected until someone opens it
deliberately, which is the right way round: forgetting to protect something is
silent, forgetting to open something is immediately obvious.

---

## Token handling for the frontend

The token goes in an `Authorization` header, not a cookie. That is what makes
disabling CSRF protection correct here — a cross-site form post cannot set
headers, and with no session cookie there is nothing for an attacker's page to
ride on. Putting the token in a cookie would change that, and the CSRF setting
would have to change with it.

Store it in memory where practical. `localStorage` survives a refresh but is
readable by any script that gets injected into the page.

On `401` with `TOKEN_EXPIRED`, send the user back to login. There is no refresh
token in V1: a one-hour access token and a login form is a complete, honest
story, and refresh tokens bring rotation, reuse detection and revocation with
them.
