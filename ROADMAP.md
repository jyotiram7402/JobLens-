# Roadmap

Each step is a self-contained increment that leaves the project in a working
state. Steps are built in order; nothing is implemented ahead of its step.

## V1

| #   | Step                  | Outcome |
| --- | --------------------- | ------- |
| 1   | Foundation            | Monorepo, Spring Boot skeleton, React skeleton, PostgreSQL + Flyway config, CI workflow, docs. **Done.** |
| 2   | Backend architecture  | Domain-oriented package structure, layering (controller to service to repository), shared kernel, error handling with correlation ids, profile strategy, JPA/Flyway configuration, API versioning, test tiers. **Done.** |
| 3   | Database + Company    | UUID/timestamp strategy, `V2__create_companies_table.sql`, company domain module (entity, repository, service, controller, DTOs), name normalization, slug generation, duplicate handling, search with pagination, dev seed data, unit/repository/controller tests. **Done.** |
| 4   | Authentication + User | `users`, `user_profiles` and preference tables; registration and login; bcrypt hashing; stateless JWT with a filter chain; `/users/me` and the career profile; company writes secured. **Done.** |
| 5   | Jobs                  | `jobs` table linked to companies, job domain module, job read/CRUD API, company-to-jobs relationship. |
| 6   | Search / filtering    | Search companies and jobs by name, location, type and tags; pagination and sorting; sensible indexes. |
| 7   | Matching              | Profile skills/preferences model, explainable scoring of a job against a profile, match score on job responses. |
| 8   | Frontend              | Real UI for login/registration, company list and detail, job list and detail; auth token handling; shared components. |
| 9   | Dashboard             | Authenticated landing view: profile summary, recent companies, recommended jobs. |
| 10  | Tracking              | Track/untrack companies and jobs, application status per tracked job, tracking views. |
| 11  | Scan                  | Image upload / camera capture page, upload endpoint, storage strategy, scan result UI, with recognition stubbed. |
| 12  | OCR / AI              | Python FastAPI service with OCR, backend-to-AI HTTP integration, candidate-to-company resolution and confidence handling. |
| 13  | AI job intelligence   | Extract structured facts from job descriptions (skills, seniority, remote/on-site) to improve matching quality. |
| 14  | Production readiness  | Application Dockerfiles, production profiles, structured logging, metrics, rate limiting, security review, test coverage pass. |
| 15  | Deployment            | Verify current free-tier options, deploy frontend/backend/database/AI service, wire CI/CD, smoke test production. |

## Beyond V1 (not being built)

Recorded so the architecture does not accidentally rule them out.

**V2**

- Visual/logo recognition instead of text-only OCR
- Mobile client (React Native or an installable PWA)
- Saved searches with email alerts
- Employer-verified company profiles
- Richer company data: size, industry, locations, funding
- Aggregation of multiple job sources with de-duplication

**V3**

- Behaviour-informed recommendations
- Resume parsing and gap analysis against a target role
- Application tracking with reminders and outcomes
- Analytics on hiring trends by area
- A public API

Anything in this section is a proposal, not a commitment.
