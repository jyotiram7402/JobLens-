# Day 03 - Spring Boot backend architecture

**Date:** 2026-09-22
**Roadmap step:** 2 - Backend architecture

> Filed as day-03, not day-02. `day-02.md` already records the Step 1
> verification work (CI, Docker, Render/Vercel/Neon configuration) that was done
> under that date, and overwriting an accurate log to satisfy a filename is not
> worth it. The daily notes stay chronological.

## Objective

Turn the Step 1 skeleton into a backend architecture that later steps can be
built into without rework: domain boundaries, layering, a single error contract,
request correlation, a profile strategy, and JPA configured so that Flyway
remains the owner of the schema.

No business features. No entities, no CRUD, no authentication — and no
placeholder controllers written purely to make the new packages look occupied.

## What was implemented

**Domain boundaries.** Seven domain packages created as boundaries only, each
holding a `package-info.java` that states its responsibility, its roadmap step
and the layout it will take when populated. No code in any of them.

**Shared kernel.** `common` now holds the pieces every future module needs:
`BaseEntity`, the error handling, the response envelopes and the web plumbing.
Its dependency rule is documented in its own `package-info.java`: `common`
imports from no module, ever.

**Error handling.** `ErrorCode` enumerates the stable, client-facing codes.
`ApplicationException` is the base for deliberate failures, with
`ResourceNotFoundException` and `DuplicateResourceException` as the first two.
`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` so that the
errors Spring MVC raises itself — unreadable JSON, wrong method, unsupported
media type — come back in the same `ApiError` shape rather than Spring's
default body.

**Request correlation.** `CorrelationIdFilter` runs first in the chain and gives
every request an id, published to the logging MDC, the `X-Correlation-Id`
response header and the error body's `traceId`. An inbound header is honoured
and truncated to 64 characters; the MDC is cleared in a `finally` block so a
pooled thread cannot leak an id into an unrelated request.

**Persistence.** The JDBC starter was replaced with Spring Data JPA.
`ddl-auto: validate` keeps Hibernate from ever touching the schema — it only
checks its mapping against what Flyway produced and refuses to start on a
mismatch. `open-in-view` is off. `BaseEntity` gives every future entity a
database-generated id, UTC audit timestamps and an optimistic-locking version.

**Configuration.** Split into `application.yml` (shared, environment-neutral),
`application-dev.yml` (local defaults that work against docker-compose),
`application-prod.yml` (no defaults at all) and `application-test.yml`. CORS
moved to a validated `CorsProperties` record.

**Tests.** The three tiers that later work will use: a plain unit test
(`CorrelationIdFilterTest`), a controller slice (`MetaControllerTest` with
`@WebMvcTest`), and an `@IntegrationTest` meta-annotation over
`@SpringBootTest` + `@ActiveProfiles("test")` used by the context smoke test.
`@DataJpaTest` is the fourth tier and starts being used in step 3.

## Package structure

```
com.joblens.api
├── JobLensApplication.java
├── config/
│   ├── CorsConfig.java
│   ├── CorsProperties.java
│   ├── JpaConfig.java
│   └── package-info.java
├── common/
│   ├── domain/BaseEntity.java
│   ├── exception/
│   │   ├── ApplicationException.java
│   │   ├── DuplicateResourceException.java
│   │   ├── ErrorCode.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── ResourceNotFoundException.java
│   ├── response/
│   │   ├── ApiError.java
│   │   └── PageResponse.java
│   ├── web/
│   │   ├── ApiRoutes.java
│   │   ├── CorrelationIdFilter.java
│   │   └── MetaController.java
│   └── package-info.java
├── user/          package-info.java only   (step 4)
├── company/       package-info.java only   (step 3)
├── job/           package-info.java only   (step 5)
├── matching/      package-info.java only   (step 7)
├── tracking/      package-info.java only   (step 10)
├── scan/          package-info.java only   (step 11)
└── notification/  package-info.java only   (V2)
```

## Dependencies

Changed: `spring-boot-starter-jdbc` → `spring-boot-starter-data-jpa`.

Nothing else was added or removed. The full set remains Web, Validation,
Data JPA, Actuator, Flyway (core + PostgreSQL), the PostgreSQL driver and the
test starter. No Spring Security yet — that belongs to step 4. No mapping
library (MapStruct, ModelMapper): records map in a line of code, and a mapping
framework earns its place only when there is mapping to do.

## Configuration

| Variable | Required on `prod` | Dev default |
| -------- | ------------------ | ----------- |
| `SPRING_PROFILES_ACTIVE` | — | `dev` |
| `JOBLENS_ENVIRONMENT` | no | `dev` |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | **yes** | localhost / 5432 / joblens / joblens / joblens |
| `DB_URL_PARAMS` | no | empty |
| `DB_POOL_SIZE` | no | 10 |
| `PORT` / `SERVER_PORT` | injected by the host | 8080 |
| `CORS_ALLOWED_ORIGINS` | **yes** | `http://localhost:5173` |
| `APP_VERSION` `LOG_LEVEL_JOBLENS` | no | — |

The profile files are the whole strategy: `dev` supplies defaults that are safe
precisely because they are useless anywhere else, and `prod` supplies none, so a
missing variable fails startup with a named placeholder instead of quietly
connecting to something local.

## Architecture decisions

1. **Domain-oriented packages, not layer-oriented.** `company/` rather than
   `controllers/`, `services/`, `repositories/`. A feature then lives in one
   place, and the package boundary is the same line a service would later be
   extracted along. Layer-first packaging spreads every change across three
   directories and marks no boundary at all.

2. **Boundaries created empty, on purpose.** Writing placeholder CRUD to fill
   the packages would create code to delete in step 3 and invite copying from a
   fake example. A `package-info.java` records the intent at no cost.

3. **Spring Data JPA over plain JDBC** (the choice left open in step 1). The
   domain is relational and heavily associated — companies to jobs to
   applications — and JPA removes the boilerplate that dominates that shape of
   code. The escape hatch is kept: `ddl-auto: validate`, no `open-in-view`, and
   Flyway owning the schema, so JPA is a mapping tool and never an authority on
   the database. Complex read queries can drop to SQL without changing the
   architecture.

4. **One error contract, defined before the first real endpoint.** Retrofitting
   a consistent error shape across endpoints that already exist is a breaking
   change; establishing it first costs nothing.

5. **`ErrorCode` is part of the public API; `message` is not.** Clients branch
   on the code, so it may not be renamed casually. The message is free to
   improve.

6. **Expected failures are returned verbatim; unexpected ones are not.**
   Exception messages routinely contain table names, SQL fragments and file
   paths. An `ApplicationException` was written for the caller and is safe; a
   `NullPointerException` is not. Unexpected failures are logged in full with
   their correlation id and reported to the client as `INTERNAL_ERROR`.

7. **Correlation ids instead of an observability platform.** A filter, an MDC
   key and a log pattern give traceable requests for no infrastructure and no
   cost, which suits the free-first rule. Honouring an inbound header means the
   same id can later span the frontend, the backend and the AI service.

8. **Typed configuration properties over `@Value`.** A misspelled property in a
   `@ConfigurationProperties` record fails at startup; a misspelled `@Value` key
   fails at runtime, or worse, silently uses a default. `@NotEmpty` on the CORS
   allowlist makes an unconfigured production deployment impossible.

9. **`PageResponse` rather than Spring Data's `Page`.** `Page` serialises to an
   unstable structure that exposes internal fields; pinning our own envelope now
   means the first paginated endpoint does not invent one.

10. **Optimistic locking from the start.** Adding a `version` column later means
    a migration across every table. It costs one column now.

11. **Real PostgreSQL in tests, not H2.** An in-memory database with a
    compatibility mode behaves differently from the thing we deploy, which is
    exactly what an integration test exists to catch.

12. **Still a modular monolith.** Reaffirmed in
    [ADR 0001](../decisions/0001-modular-monolith.md): one deployable unit, one
    database, one transaction boundary; boundaries enforced by packages, not
    processes; Kafka, Redis and microservices postponed until something
    measurably requires them.

## Files created

```
backend/src/main/java/com/joblens/api/common/package-info.java
backend/src/main/java/com/joblens/api/common/domain/BaseEntity.java
backend/src/main/java/com/joblens/api/common/exception/ApplicationException.java
backend/src/main/java/com/joblens/api/common/exception/DuplicateResourceException.java
backend/src/main/java/com/joblens/api/common/exception/ErrorCode.java
backend/src/main/java/com/joblens/api/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/joblens/api/common/exception/ResourceNotFoundException.java
backend/src/main/java/com/joblens/api/common/response/ApiError.java
backend/src/main/java/com/joblens/api/common/response/PageResponse.java
backend/src/main/java/com/joblens/api/common/web/ApiRoutes.java
backend/src/main/java/com/joblens/api/common/web/CorrelationIdFilter.java
backend/src/main/java/com/joblens/api/common/web/MetaController.java
backend/src/main/java/com/joblens/api/config/CorsProperties.java
backend/src/main/java/com/joblens/api/config/JpaConfig.java
backend/src/main/java/com/joblens/api/config/package-info.java
backend/src/main/java/com/joblens/api/{user,company,job,matching,tracking,scan,notification}/package-info.java
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application-prod.yml
backend/src/test/java/com/joblens/api/support/IntegrationTest.java
backend/src/test/java/com/joblens/api/common/web/MetaControllerTest.java
backend/src/test/java/com/joblens/api/common/web/CorrelationIdFilterTest.java
docs/daily/day-03.md
```

## Files modified

```
backend/pom.xml                              starter-jdbc -> starter-data-jpa
backend/src/main/java/.../JobLensApplication.java   documentation
backend/src/main/java/.../config/CorsConfig.java    now uses CorsProperties
backend/src/main/resources/application.yml   profiles, JPA, Jackson, logging,
                                             error-response hardening
backend/src/test/resources/application-test.yml     matches the new shape
backend/src/test/java/.../JobLensApplicationTests.java  uses @IntegrationTest
render.yaml                                  SPRING_PROFILES_ACTIVE=prod
.env.example                                 new variables documented
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md
```

## Files removed

```
backend/src/main/java/com/joblens/api/health/MetaController.java      -> common/web/
backend/src/main/java/com/joblens/api/common/web/ApiError.java        -> common/response/
backend/src/main/java/com/joblens/api/common/web/GlobalExceptionHandler.java -> common/exception/
```

## Commands for the separate test machine

```bash
docker compose -f docker/docker-compose.yml up -d
docker exec -it joblens-postgres createdb -U joblens joblens_test
```

```bash
cd backend
mvn --batch-mode clean verify
```

```bash
cd backend && mvn spring-boot:run
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/v1/meta
```

Checks worth running by hand, because they exercise the new machinery:

```bash
# Correlation id echoed back, and reused when supplied
curl -i -H "X-Correlation-Id: my-trace-1" http://localhost:8080/api/v1/meta

# Unknown path -> ApiError JSON, not an HTML error page
curl -i http://localhost:8080/api/v1/nope

# Wrong method -> 405 in the same ApiError shape
curl -i -X DELETE http://localhost:8080/api/v1/meta
```

```bash
# The prod profile must refuse to start with no database configuration
cd backend && SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `mvn`, `java`,
`javac`, `npm`, `node`, `docker`, `docker compose`, `python`, `pip`. Nothing in
this step has been compiled, started or tested. No claim is made that the
backend builds, that the context loads, that the tests pass, or that Flyway
runs.

## Things that could not be verified

- **That any of it compiles.** This is the largest amount of Java written in one
  step so far, and it has never been through a compiler. The most likely
  failures are in `GlobalExceptionHandler`, which overrides
  `ResponseEntityExceptionHandler` methods whose exact signatures changed in
  Spring 6 (`HttpStatusCode` rather than `HttpStatus`).
- **That `@WebMvcTest(MetaController.class)` starts.** The slice pulls in
  `WebMvcConfigurer` and `Filter` beans, so `CorsConfig` and
  `CorrelationIdFilter` are loaded too and `CorsProperties` must bind from the
  test profile. If binding fails, the fix is `@Import` or explicit test
  properties.
- **That `@EnableJpaAuditing` is happy with no entities present.** It should be,
  but it has not been started.
- **That the profile files resolve as intended**, in particular that `dev`
  supplies its defaults and `prod` genuinely fails fast on a missing variable.
- **That Flyway still runs**, now that the datasource is configured through
  Data JPA rather than the JDBC starter.
- Dependency versions remain unresolved against a registry.
- No `package-lock.json` yet, unchanged from Step 1.

## Next step

Step 3 — Database + Company: the `companies` table as `V2__companies.sql`
including the `BaseEntity` columns, a `Company` entity, repository, service and
controller, request/response DTO records, `GET /api/v1/companies` returning a
`PageResponse`, development seed data, and the first `@DataJpaTest`.

Not started. Step 2 should be pushed and go green in CI first.
