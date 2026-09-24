# Day 05 - Authentication, JWT and the user career profile

**Date:** 2026-09-24
**Roadmap step:** 4 - Authentication + User Profile

> Filed as day-05; [README.md](README.md) maps day numbers to step numbers.

## Objective

Give JobLens accounts: registration, login, stateless JWT authentication, a
protected current-user endpoint, and the career profile that step 7 will match
jobs against. Implemented in-house with Spring Security, JWT and bcrypt — no
paid identity provider.

No job matching, no job domain.

## Database changes

`V3__create_users_and_profile_tables.sql`. V1 and V2 untouched.

```
users                       id, email (unique, lowercased), password_hash,
                            first_name, last_name, role, active, audit columns
user_profiles               user_id (unique FK, ON DELETE CASCADE), headline,
                            summary, years_of_experience, current_job_title,
                            remote_preference, audit columns
user_skills                 PK (profile_id, normalized_name)
user_preferred_roles        PK (profile_id, normalized_title)
user_preferred_locations    PK (profile_id, normalized_name)
```

Details worth recording:

- **Email is stored lowercased and trimmed**, with a `CHECK (email =
  lower(email))` to keep it that way. That makes `ux_users_email` genuinely
  case-insensitive without citext or a functional index.
- `password_hash` is `VARCHAR(100)`, not 60, because the
  `DelegatingPasswordEncoder` prefixes the algorithm (`{bcrypt}$2a$10$...`).
  That prefix is what allows a later upgrade without invalidating passwords.
- **`current_job_title`, not `current_role`** — `CURRENT_ROLE` is a reserved SQL
  word, and a column needing quotes everywhere is a permanent tax.
- `CHECK` constraints bound years of experience to 0–60 and restrict `role` and
  `remote_preference` to known values, so bad data cannot arrive by any route.
- No extra indexes: the unique FK on `user_profiles.user_id` already indexes the
  only way that table is read, and the composite primary keys lead with
  `profile_id`, which is how the collections are always queried.

## Authentication architecture

```
POST /auth/register          POST /auth/login
  validate                     look up by normalized email
  normalize email              verify password (bcrypt)
  hash password                check active flag
  save user + empty profile    issue JWT
  201, no token                200 + accessToken
```

```
Authenticated request
  Authorization: Bearer <jwt>
    → JwtAuthenticationFilter — verify signature, issuer, expiry
    → AuthenticatedUser(id, email, role) into the SecurityContext
    → authorization (default deny)
    → controller takes the id from the context, never from the request
```

Token issuing and verification live in `com.joblens.api.security`, separate from
the `user` module. The user module owns accounts and profiles; the security
package owns how a request proves who it is. A later change — refresh tokens, a
denylist, OAuth — happens there without touching the domain.

## JWT flow

Claims are `sub` (user id), `email`, `role`, `iss`, `iat`, `exp`. Nothing else.
A JWT is signed but **not encrypted**, so anyone holding it can read every
claim; it carries the minimum needed to authorize and no personal data beyond
the identifying email. A test asserts the exact claim set, so a future addition
has to be deliberate.

`JWT_SECRET` has no default in `application.yml` or the prod profile, and
`JwtService` refuses to start if it is under 256 bits. The dev and test profiles
carry obviously-fake local values so `mvn spring-boot:run` needs no setup —
written to be unusable rather than plausible.

Verification touches no database. The principal is built from the claims,
because a query before every authenticated request is most of the budget on a
0.1 CPU container. **The honest cost: a token stays valid until it expires, so
deactivating an account does not end a session already in progress.** One-hour
lifetime is the mitigation; real revocation needs a denylist, which is a feature
with infrastructure behind it.

A bad token does not fail the request in the filter. The reason is recorded as a
request attribute and the chain continues unauthenticated, so authorization
decides. A bad token on a public endpoint is then ignored rather than turning a
working request into a 401, and the entry point still has the reason available
when the endpoint does require authentication — which is how `TOKEN_EXPIRED` and
`TOKEN_INVALID` stay distinguishable.

## Security configuration

- `SessionCreationPolicy.STATELESS` — no session is ever created or consulted.
- **CSRF disabled, and correct here.** CSRF defends against a browser attaching
  *ambient* credentials — cookies or basic auth. This API authenticates with an
  `Authorization` header a client must set deliberately, and a cross-site form
  post cannot set headers. This stops being true the moment a token goes in a
  cookie; that is the line to watch.
- Default deny (`anyRequest().authenticated()`). Public routes are listed
  individually: the two auth endpoints, `/actuator/health`, `/api/v1/meta`, and
  company **reads**. Company **writes** now require authentication, tightening
  what step 3 left open.
- `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` render 401 and
  403 as `ApiError`. Spring Security rejects those requests inside the filter
  chain, before any `@RestControllerAdvice`, so without these the API would have
  two error formats.
- **CORS moved to a `CorsConfigurationSource` bean.** Security runs its own
  filter chain, and a request it rejects never reaches Spring MVC — so the
  previous `WebMvcConfigurer` CORS settings would not have applied to a 401, and
  the browser would have shown an opaque CORS error instead of a readable one.

## User profile design

```
users
 └── user_profiles (1:1)
       ├── skills
       ├── preferred roles
       └── preferred locations
```

Account and profile are separate because they change for different reasons: one
is identity and credentials, the other is career data. Keeping them together
would mean loading a password hash every time the matcher wants a skill list.

The profile is created **with** the account, so every user always has one and no
endpoint handles a missing profile or risks creating a second.

The three collections are JPA **element collections**, not entities. Each row is
a value with no identity of its own: it exists because the profile says so and
disappears with it. That gives cascade and orphan removal for free and avoids
three entity classes and three repositories. Each stores the text as typed plus
a normalized form.

`TextNormalizer` moved into `common` for this, with `CompanyNameNormalizer`
delegating to it. Sharing the rules is the point: a skill canonicalised one way
on a profile and another way on a job would not join in step 7. It is also what
makes `Set` deduplication work, so `Java` and `java` are one skill.

No `@EntityGraph` over the three collections: fetch-joining all of them produces
a cartesian product — 50 skills × 20 roles × 20 locations is 20,000 rows for
Hibernate to deduplicate in memory. Three small lazy queries inside the same
transaction is cheaper.

## APIs

| Method | Path | Auth | Notes |
| ------ | ---- | ---- | ----- |
| `POST` | `/api/v1/auth/register` | public | 201, no token |
| `POST` | `/api/v1/auth/login` | public | 200 + `accessToken` |
| `GET` | `/api/v1/users/me` | required | Account + profile |
| `GET` | `/api/v1/users/me/profile` | required | |
| `PUT` | `/api/v1/users/me/profile` | required | Full replacement |

Full examples in [../api/auth-and-users.md](../api/auth-and-users.md).

**Registration does not log the user in.** One way to obtain a token means one
code path to audit, and email verification — the obvious next requirement — sits
exactly where auto-login would be. Not issuing a token now means adding
verification later does not have to take one away, which would break the
frontend. Cost: one extra request at signup, which the frontend can hide.

## Files created

```
backend/src/main/resources/db/migration/V3__create_users_and_profile_tables.sql
backend/src/main/java/com/joblens/api/common/text/TextNormalizer.java
backend/src/main/java/com/joblens/api/common/domain/NormalizedText.java
backend/src/main/java/com/joblens/api/config/SecurityConfig.java
backend/src/main/java/com/joblens/api/security/JwtProperties.java
backend/src/main/java/com/joblens/api/security/JwtService.java
backend/src/main/java/com/joblens/api/security/JwtAuthenticationFilter.java
backend/src/main/java/com/joblens/api/security/AuthenticatedUser.java
backend/src/main/java/com/joblens/api/security/CurrentUser.java
backend/src/main/java/com/joblens/api/security/TokenExpiredException.java
backend/src/main/java/com/joblens/api/security/InvalidTokenException.java
backend/src/main/java/com/joblens/api/security/RestAuthenticationEntryPoint.java
backend/src/main/java/com/joblens/api/security/RestAccessDeniedHandler.java
backend/src/main/java/com/joblens/api/security/package-info.java
backend/src/main/java/com/joblens/api/user/AuthController.java
backend/src/main/java/com/joblens/api/user/AuthService.java
backend/src/main/java/com/joblens/api/user/UserController.java
backend/src/main/java/com/joblens/api/user/UserService.java
backend/src/main/java/com/joblens/api/user/UserRepository.java
backend/src/main/java/com/joblens/api/user/UserProfileRepository.java
backend/src/main/java/com/joblens/api/user/domain/{User,UserProfile,Role,RemotePreference}.java
backend/src/main/java/com/joblens/api/user/dto/{RegisterRequest,LoginRequest,AuthResponse,
    RegistrationResponse,UserResponse,CurrentUserResponse,UserProfileResponse,
    UpdateUserProfileRequest}.java
backend/src/main/java/com/joblens/api/user/exception/{EmailAlreadyRegistered,
    InvalidCredentials,AccountInactive,UserNotFound}Exception.java
backend/src/test/java/com/joblens/api/security/JwtServiceTest.java
backend/src/test/java/com/joblens/api/user/AuthServiceTest.java
backend/src/test/java/com/joblens/api/user/AuthAndProfileIntegrationTest.java
docs/api/auth-and-users.md
docs/daily/day-05.md
```

## Files modified

```
backend/pom.xml                        spring-boot-starter-security, jjwt 0.12.6,
                                       spring-security-test
backend/.../config/CorsConfig.java     WebMvcConfigurer -> CorsConfigurationSource bean
backend/.../common/exception/ErrorCode.java   eight authentication codes
backend/.../company/CompanyNameNormalizer.java  delegates to TextNormalizer
backend/.../user/package-info.java     documents the populated module
application.yml / -dev.yml / -test.yml joblens.jwt.*
src/test/.../MetaControllerTest.java   security auto-config excluded, JwtService mocked
src/test/.../CompanyControllerTest.java  same
render.yaml, .env.example              JWT_SECRET (sync:false), JWT_EXPIRATION
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md, docs/daily/README.md
```

## Tests created

| Tier | Class | Covers |
| ---- | ----- | ------ |
| Unit | `JwtServiceTest` | Round trip; exact claim set; expired token; token signed with another key; wrong issuer; garbage; startup refusal on a short secret |
| Unit | `AuthServiceTest` | Password is hashed and the raw one never persisted; email lowercased; empty profile created; registration issues no token; duplicate email; password beyond bcrypt's 72 bytes; valid login; wrong password; unknown email producing the *same* error; inactive account rejected only after the password is verified |
| Integration | `AuthAndProfileIntegrationTest` | Real filter chain and real database: registration response contains no credential; invalid email and short password; duplicate email regardless of casing; identical responses for wrong-password and unknown-account; 401 without a token; 401 for a malformed token; public endpoints still reachable; company writes now require auth; `/users/me` works with a token and exposes no hash; two users cannot see each other's profile; empty profile on a new account; profile update and read-back; skill deduplication; collection replacement; invalid years of experience; blank skill names; too many skills |

`AuthServiceTest` uses a real `BCryptPasswordEncoder` rather than a mock — the
point of several tests is that hashing genuinely happens, and a mocked encoder
would prove nothing.

## Security checks performed by code inspection

Commands cannot be run here, so this was a read-through, not a scan.

| Check | Finding |
| ----- | ------- |
| Plain-text passwords | None. The raw password exists only on `RegisterRequest`/`LoginRequest` and is hashed before `User` is constructed. `User.register` accepts a hash only. |
| Password in responses | No DTO has a password field. An integration test asserts the registration response body contains neither the password nor the string `password`. |
| Password/hash/token in logs | Every log statement identifies a user by id. `User.toString()` is overridden to emit only the id, because entities reach log messages more often than intended. `JwtService` never logs a token, and `InvalidTokenException` deliberately carries no library detail. |
| Hard-coded JWT secret | None in `application.yml` or the prod profile — a missing `JWT_SECRET` fails startup. Dev and test carry clearly-marked local placeholders. `render.yaml` uses `sync: false`. |
| Weak JWT secret | `JwtService` refuses to start below 256 bits, with a message showing how to generate one. |
| Sensitive data in JWT | Claims limited to `sub`, `email`, `role`, `iss`, `iat`, `exp`, asserted by a test. |
| Token forgery | Signature verified, plus `requireIssuer`. A test forges a token with a different key and expects rejection. |
| SQL injection | No string-concatenated queries anywhere. Derived method names and JPA only. |
| IDOR | No `/users/{id}` route and no `userId` field in any DTO. The id always comes from the security context. Verified by a test where one user's update leaves another's profile untouched. |
| Session-based auth enabled by accident | `SessionCreationPolicy.STATELESS`. |
| Public protected endpoints | `anyRequest().authenticated()` is the default; public routes are enumerated. A test asserts `/users/me` and company writes are protected. |
| Overly permissive CORS | Explicit origin allowlist, `@NotEmpty`-validated. No wildcard, no `allowCredentials`. Actuator is not exposed to CORS at all. |
| Account enumeration | Login is identical for wrong-password and unknown-account, and spends the same time on both by verifying against a dummy hash. Registration does reveal a taken address (409) — accepted and documented, since the alternative needs email delivery. |
| Unbounded input | Every string field has a length cap matching its column; collections capped at 20/20/50; page size capped at 50. |
| Bcrypt truncation | Passwords capped at 72 bytes, checked in UTF-8 bytes, because bcrypt silently ignores the rest. |
| Stack traces exposed | Unchanged from step 2: unexpected exceptions return `INTERNAL_ERROR` with only a `traceId`. |

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
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"SecurePassword123!","firstName":"John","lastName":"Doe"}'
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"SecurePassword123!"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "$TOKEN"
```

```bash
curl -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN"
```

```bash
# Expect 401 UNAUTHENTICATED
curl -i http://localhost:8080/api/v1/users/me
# Expect 401 TOKEN_INVALID
curl -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer not.a.token"
# Expect 401 INVALID_CREDENTIALS, identical for both
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"wrong password here"}'
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"nobody@example.com","password":"wrong password here"}'
```

```bash
curl -i -X PUT http://localhost:8080/api/v1/users/me/profile \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"headline":"Software Engineer","yearsOfExperience":2,"remotePreference":"HYBRID",
       "skills":["Java","java","Spring Boot"],"preferredLocations":["Pune","Remote"]}'
```

The `skills` array above should come back with two entries, not three.

```bash
# The prod profile must refuse to start with no JWT_SECRET
cd backend && SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

```bash
# Generate a real secret for Render
openssl rand -base64 48
```

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `mvn`, `java`,
`javac`, `npm`, `node`, `docker`, `docker compose`, `python`, `pip`, `openssl`,
`curl`. Nothing here has been compiled, started or tested. No claim is made that
the migration applies, that the filter chain works, that a token can be issued
or verified, or that any test passes.

## Things that could not be verified

- **That it compiles.** Highest-risk areas: the JJWT 0.12.x builder/parser API
  (`verifyWith`, `parseSignedClaims`, `requireIssuer` — renamed from 0.11.x), and
  `@UuidGenerator` interactions unchanged from step 3.
- **That `@WebMvcTest` still works for the two existing slice tests.** Adding
  Spring Security changes what those slices contain. I excluded
  `SecurityAutoConfiguration` and `SecurityFilterAutoConfiguration` and mocked
  `JwtService`, because `JwtAuthenticationFilter` is a `@Component Filter` and
  therefore lands in the slice. If those tests fail, this is the first place to
  look — it is the least certain change in this step.
- **That Hibernate maps three element collections with `@AttributeOverride`
  onto the migration's column names.** `ddl-auto: validate` will say at startup.
- **That element-collection replacement behaves.** `update` clears and refills
  the managed set; Hibernate should delete and reinsert. Untested here.
- **Whether the `CorsConfigurationSource` switch preserved behaviour.** Security
  should pick the bean up via `http.cors(withDefaults())`, but the previous
  MVC-level config is gone, so a mistake shows up as a browser CORS failure
  rather than a test failure.
- **Whether `@Transactional` on the integration test interacts badly with the
  real filter chain** — it should not, same thread, but it has not run.
- **That the timing-equalisation on login actually equalises.** It does the same
  bcrypt work in both branches by inspection, but measuring it needs a running
  server.
- Dependency versions, including jjwt 0.12.6, remain unresolved against a
  registry.

## Next step

Step 5 — Jobs: `V4__create_jobs_table.sql` referencing `companies`, the `job`
module, job read endpoints, the company-to-jobs relationship, and job writes
behind the authentication built today.

Not started.
