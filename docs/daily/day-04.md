# Day 04 - Database foundation and the Company domain

**Date:** 2026-09-24
**Roadmap step:** 3 - Database + Company

> Filed as day-04. Day numbers and step numbers stopped lining up on day 2, when
> finishing step 1 properly took an extra session. Renaming the files now would
> break existing links, so [docs/daily/README.md](README.md) maps one to the
> other instead.

## Objective

Put the first real business table in the database and build the Company domain
on top of it: entity, repository, service, DTOs, controller, validation,
normalization, slug generation, duplicate handling, search, and tests at each
tier.

No authentication, jobs, matching, tracking, OCR or notifications.

## Database design

```
companies
  id               UUID         PK, application-generated, time-ordered
  name             VARCHAR(200) NOT NULL   as typed, for display
  normalized_name  VARCHAR(200) NOT NULL   derived; UNIQUE
  slug             VARCHAR(220) NOT NULL   public URL id; UNIQUE; never changes
  description      VARCHAR(2000)
  website_url      VARCHAR(500)
  careers_url      VARCHAR(500)
  logo_url         VARCHAR(500)
  industry         VARCHAR(120)
  location         VARCHAR(200)            free text for V1
  active           BOOLEAN      NOT NULL DEFAULT TRUE
  created_at       TIMESTAMPTZ  NOT NULL
  updated_at       TIMESTAMPTZ  NOT NULL
  version          BIGINT       NOT NULL DEFAULT 0
```

Indexes: `ux_companies_normalized_name`, `ux_companies_slug`. Both unique, both
carrying real weight — the first is what actually prevents duplicates, the
second what makes slugs safe to address by.

No index on `active`: a boolean has two values and cannot narrow a search enough
to beat a scan, so PostgreSQL would ignore it. Also no index that helps the
current `LIKE '%term%'` search, because no B-tree can; that is a deliberate V1
trade-off revisited in step 6 with `pg_trgm`.

Two `CHECK` constraints reject names that are technically strings but blank.

## Company domain design

```
company/
  CompanyController.java        HTTP only
  CompanyService.java           business rules, transaction boundary
  CompanyRepository.java        persistence
  CompanyNameNormalizer.java    pure function
  SlugGenerator.java            pure function
  domain/Company.java           entity, never leaves the service layer
  dto/                          CreateCompanyRequest, UpdateCompanyRequest,
                                CompanyResponse, CompanySummary, UrlPatterns
  exception/                    CompanyNotFoundException,
                                CompanyAlreadyExistsException,
                                IllegalCompanyNameException
```

The entity has no public setters. State changes go through `create(...)`,
`updateDetails(...)`, `activate()` and `deactivate()`, so `normalizedName` is
always recomputed from `name` and can never be set independently — the two
disagreeing would silently break both duplicate detection and search.

## API endpoints

| Method | Path | Success | Failures |
| ------ | ---- | ------- | -------- |
| `POST` | `/api/v1/companies` | `201` + `Location` | `400`, `409` |
| `GET` | `/api/v1/companies?search=&page=&size=` | `200` | `400` |
| `GET` | `/api/v1/companies/{id}` | `200` | `400`, `404` |
| `GET` | `/api/v1/companies/by-slug/{slug}` | `200` | `404` |
| `PUT` | `/api/v1/companies/{id}` | `200` | `400`, `404`, `409` |

Full examples in [../api/companies.md](../api/companies.md).

No `DELETE`. Jobs, tracking rows and scan history will reference companies;
deleting one would orphan those rows or cascade into deleting a user's saved
data as a side effect of an administrative action. The `active` flag covers the
real need. Reasoning recorded in the API doc rather than implemented.

## Validation rules

| Field | Rule |
| ----- | ---- |
| `name` | required, non-blank, ≤ 200 chars, must normalize to something non-empty |
| `description` | ≤ 2000 chars |
| `websiteUrl`, `careersUrl`, `logoUrl` | ≤ 500 chars, `http`/`https` only |
| `industry` | ≤ 120 chars |
| `location` | ≤ 200 chars |
| `page` | ≥ 0 |
| `size` | 1–50 |

Every length matches its column, so oversized input fails with a readable
message instead of a constraint violation at insert time.

The URL scheme check is the one that matters for security: without it a client
could store `javascript:...` in `websiteUrl` and the frontend would later render
it as a link.

The "must normalize to something" rule cannot be expressed in Bean Validation
because it depends on the normalizer, so the service enforces it and reports it
through the same `VALIDATION_ERROR` code rather than as a 500.

## Migration details

`V2__create_companies_table.sql`. `V1__baseline.sql` already existed and was
left untouched — applied migrations are never edited or renamed.

Development seed data is `db/seed/V9001__dev_seed_companies.sql`, and
`db/seed` is added to `spring.flyway.locations` **only by the dev profile**, so
those rows cannot reach production. Five companies, chosen to exercise real
behaviour: mixed case, extra spacing, an accent, punctuation, and one inactive
row so that search filtering can be checked by eye. The version number is far
above the real migrations so seed data always runs last.

## Architecture decisions

1. **Time-ordered UUID primary keys.** Ids appear in URLs, so a sequential
   `bigint` would publish how many companies exist and let anyone enumerate the
   table by counting upwards — a real problem for a product whose value is its
   dataset. The cost is 16 bytes instead of 8 here and in every future foreign
   key, and it is affordable specifically because the UUIDs are *time-ordered*:
   random v4 UUIDs scatter inserts across the whole B-tree and fragment the
   index, while time-ordered values append at the right-hand edge like a
   sequence. UUID v4 would have been the version that costs real performance for
   the sake of sounding advanced.

   `BaseEntity` changed from `Long`/IDENTITY to `UUID` to make this possible.
   It was free today because no entity existed yet; after step 4 it would have
   been a migration across every table.

2. **Two identifiers, different jobs.** The UUID is the stable key other
   resources reference; the slug is the readable one for URLs. A slug is
   assigned once and **never regenerated**, even on rename, because it already
   exists in links and caches.

3. **`Instant` stored as `timestamptz`, always UTC.** An `Instant` has no zone
   of its own and cannot be misread as local time. The database server, the
   container and a laptop disagree about "local"; none of them may influence a
   stored value.

4. **Rule-based normalization, never AI.** The result goes into a unique index,
   so it must be reproducible on every machine forever, with no model version or
   network call in the loop.

5. **Normalization deliberately does not strip legal suffixes.** `Acme Ltd` and
   `Acme` stay two records. Deciding `Ltd`/`Inc`/`GmbH` are noise is entity
   resolution: it needs per-jurisdiction judgement, it merges genuinely distinct
   legal entities, and a unique index makes a wrong merge permanent. That is the
   AI company-resolution step, where confidence scores and review exist.

6. **Duplicates are handled in two layers, and both are needed.** The service
   lookup exists to produce a good error naming the existing slug. The unique
   index is what actually prevents the duplicate, because the check and the
   insert are not atomic and only the database can settle a concurrent race.
   Application check alone looks right and fails under concurrency; constraint
   alone produces a 500.

7. **Domain-specific error codes.** `COMPANY_NOT_FOUND` and
   `COMPANY_ALREADY_EXISTS` rather than the generic codes, because a client can
   react usefully to "this company already exists" by linking to it. They are
   registered in the shared `ErrorCode` enum so it stays the single list of
   everything the API can return.

8. **Fixed sort order on search.** A client-supplied `sort` parameter lets a
   caller order by any mapped property, including unindexed ones, and turns a
   typo into a 500. Configurable sorting arrives in step 6 with an allowlist.

9. **Page size capped at 50.** Without a ceiling, `?size=100000` is a free
   denial-of-service against a 512 MB container.

10. **PUT, not PATCH.** A full replacement of the editable fields, which is what
    PUT means. PATCH needs a way to distinguish "absent" from "set to null", and
    that is worth building when something needs it.

11. **No `DELETE` endpoint.** See above.

## Files created

```
backend/src/main/resources/db/migration/V2__create_companies_table.sql
backend/src/main/resources/db/seed/V9001__dev_seed_companies.sql
backend/src/main/java/com/joblens/api/company/CompanyController.java
backend/src/main/java/com/joblens/api/company/CompanyService.java
backend/src/main/java/com/joblens/api/company/CompanyRepository.java
backend/src/main/java/com/joblens/api/company/CompanyNameNormalizer.java
backend/src/main/java/com/joblens/api/company/SlugGenerator.java
backend/src/main/java/com/joblens/api/company/domain/Company.java
backend/src/main/java/com/joblens/api/company/dto/CreateCompanyRequest.java
backend/src/main/java/com/joblens/api/company/dto/UpdateCompanyRequest.java
backend/src/main/java/com/joblens/api/company/dto/CompanyResponse.java
backend/src/main/java/com/joblens/api/company/dto/CompanySummary.java
backend/src/main/java/com/joblens/api/company/dto/UrlPatterns.java
backend/src/main/java/com/joblens/api/company/exception/CompanyNotFoundException.java
backend/src/main/java/com/joblens/api/company/exception/CompanyAlreadyExistsException.java
backend/src/main/java/com/joblens/api/company/exception/IllegalCompanyNameException.java
backend/src/test/java/com/joblens/api/support/RepositoryTest.java
backend/src/test/java/com/joblens/api/company/CompanyNameNormalizerTest.java
backend/src/test/java/com/joblens/api/company/SlugGeneratorTest.java
backend/src/test/java/com/joblens/api/company/CompanyServiceTest.java
backend/src/test/java/com/joblens/api/company/CompanyRepositoryTest.java
backend/src/test/java/com/joblens/api/company/CompanyControllerTest.java
docs/api/companies.md
docs/daily/README.md
docs/daily/day-04.md
```

## Files modified

```
backend/.../common/domain/BaseEntity.java      Long/IDENTITY -> time-ordered UUID
backend/.../common/exception/ErrorCode.java    COMPANY_NOT_FOUND, COMPANY_ALREADY_EXISTS
backend/.../company/package-info.java          documents the populated module
backend/src/main/resources/application-dev.yml db/seed added to flyway locations
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md, docs/VERIFICATION.md
```

## Tests created

| Tier | Class | Covers |
| ---- | ----- | ------ |
| Unit | `CompanyNameNormalizerTest` | Every documented rule, non-Latin scripts, names that normalize to nothing, `LIKE` wildcards being stripped, and the explicit non-goal of suffix stripping |
| Unit | `SlugGeneratorTest` | Base slug, collision suffixes, column-length limits, unslugifiable names |
| Unit | `CompanyServiceTest` | Creation, blank-to-null handling, duplicate rejection, not-found, rename recomputing the normalized name while keeping the slug, rename collisions |
| Repository | `CompanyRepositoryTest` | Id and timestamp assignment, all three lookups, both unique constraints, search matching, inactive exclusion, database-side paging |
| Controller | `CompanyControllerTest` | `201` + `Location`, validation failures with field details, `409`, malformed JSON, malformed UUID, `404`, the page envelope, page-size cap, and that `normalizedName`/`version` never appear in a response |

Writing `CompanyServiceTest` found a real NPE: the rename-collision check called
`clash.get().getId().equals(id)`, which throws when the id is null. Flipped to
`id.equals(clash.get().getId())`.

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
curl -i -X POST http://localhost:8080/api/v1/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tata Consultancy Services","industry":"Information Technology","location":"Pune, India"}'
```

```bash
# Duplicate differing only in case -> expect 409 COMPANY_ALREADY_EXISTS
curl -i -X POST http://localhost:8080/api/v1/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"TATA CONSULTANCY SERVICES"}'
```

```bash
curl -i 'http://localhost:8080/api/v1/companies?search=tata'
curl -i 'http://localhost:8080/api/v1/companies?size=5000'
curl -i http://localhost:8080/api/v1/companies/by-slug/tata-consultancy-services
curl -i http://localhost:8080/api/v1/companies/not-a-uuid
```

```sql
-- In psql: confirm the migration and the seed data
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT name, normalized_name, slug, active FROM companies ORDER BY name;
```

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `mvn`, `java`,
`javac`, `npm`, `node`, `docker`, `docker compose`, `python`, `pip`, `psql`.
Nothing here has been compiled, started or tested. No claim is made that the
migration applies, that the entity mapping validates, that any endpoint responds
or that any test passes.

## Things that could not be verified

- **That it compiles.** Unverified as always, with two specific risks:
  `@UuidGenerator(style = Style.TIME)` (Hibernate 6 API, not previously used
  here) and `@MockBean` in `CompanyControllerTest` — correct on Spring Boot
  3.3.5, but deprecated in 3.4 in favour of `@MockitoBean`, so it will need
  changing whenever we upgrade.
- **That Hibernate's mapping validates against the migration.** `ddl-auto:
  validate` compares them at startup; a mismatch in a column name, length or
  type fails the boot. That check has never run. It is the single most likely
  failure of this step.
- **That the UUID mapping works end to end** — that Hibernate writes a
  PostgreSQL `uuid` rather than a `bytea` or a `varchar`, and that
  `@UuidGenerator` assigns before insert.
- **That `@RepositoryTest` connects to real PostgreSQL** rather than silently
  falling back to an embedded database.
- **That `:term IS NULL` behaves in the JPQL query.** Passing a null parameter
  into a comparison works differently across JPA providers and sometimes needs a
  cast in PostgreSQL. If the "null term returns everything" test fails, this is
  why.
- **Whether `location` should have been part of `Company.create(...)`** rather
  than inconsistent with `updateDetails(...)` — it is now, but only by reading.
- Dependency versions remain unresolved against a registry; still no
  `package-lock.json`.

## Next step

Step 4 — Authentication + User: the `users` table, registration and login,
BCrypt hashing, JWT issuing and validation, Spring Security configuration, and
securing the company write endpoints while leaving reads public.

Not started.
