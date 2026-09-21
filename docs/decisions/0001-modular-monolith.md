# ADR 0001 - Build the backend as a modular monolith

- **Status:** Accepted
- **Date:** 2026-09-21

## Context

JobLens V1 needs a backend serving companies, jobs, users, matching and
tracking, plus a future OCR/AI capability. The project is built by one person,
on free-tier infrastructure, as a portfolio piece that has to be explainable in
an interview.

Microservices are the obvious "impressive" choice and the obvious wrong one:
the domain boundaries are not yet known, the data is heavily interrelated, and
every extra deployable unit costs a free-tier allowance and a cold start.

## Decision

The backend is a single Spring Boot application, organised as a modular
monolith. Each domain (`company`, `job`, `user`, `matching`, `tracking`) gets
its own package owning its entities, repository and service. Cross-domain calls
go through a domain's service, never directly into another domain's repository.

The single exception is the OCR/AI capability, which will be a separate
stateless Python service. It is split out because of ecosystem and resource
profile, not because of domain boundaries, and it owns no data.

Redis and Kafka are not adopted.

## Consequences

**Positive**

- One deployment, one database, one transaction boundary.
- Refactoring a boundary is a code change, not a data migration.
- Cheap to run and quick to start on free hosting.
- Simple to explain and to reason about.

**Negative**

- No independent scaling per domain. Acceptable at V1 traffic.
- Module boundaries are a convention and can be violated; this needs discipline
  in code review, and possibly an ArchUnit test later.
- Extracting a service later is real work, though the package boundary marks
  exactly where the cut would be.

## Revisit when

A specific module demonstrably needs independent scaling or deployment, or the
codebase grows past what one deployable unit can sensibly hold.
