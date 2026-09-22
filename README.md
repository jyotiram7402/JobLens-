# JobLens

**See a company. Discover the opportunity.**

## Problem statement

You walk past company offices, buildings and signage every day without knowing
which of them are hiring, what they do, or whether any of their openings fit
you. Finding out means remembering the name, searching later, digging through
job boards, and manually judging whether a role is a match. Most people never
bother.

## Product vision

JobLens closes that gap at the moment of curiosity. Point your phone at a
company sign and JobLens identifies the company, tells you about it, surfaces
its legitimate public job openings, and scores them against your profile.

The eventual end-to-end flow:

```
Image / camera
  → OCR / AI detects publicly visible company branding
  → identify and verify the company
  → show company information
  → discover legitimate public job opportunities
  → match jobs against the user's profile
  → track companies and jobs
```

JobLens only uses **publicly available** company and job information.

## V1 scope

V1 delivers that flow end to end, built incrementally:

- Company records, with search and filtering
- Legitimate public job listings tied to companies
- User accounts, authentication and a profile
- Job matching against the user's profile
- A React web client: home, scan, company pages, job pages, dashboard, profile
- Company and job tracking
- Image scan → OCR → company identification
- Deployed to free-tier infrastructure with CI

Not in V1: mobile apps, employer accounts, real-time notifications, messaging,
recommendation models trained on user behaviour, Kafka, Redis.

## Technology stack

| Layer          | Choice                                   |
| -------------- | ---------------------------------------- |
| Frontend       | React 18, TypeScript, Vite, React Router |
| Backend        | Java 21, Spring Boot 3.3, Maven          |
| Database       | PostgreSQL 16, Flyway migrations         |
| AI / OCR       | Python, FastAPI (later step)             |
| Infrastructure | Docker (local), GitHub Actions (CI)      |

## High-level architecture

```
React + TypeScript  ──HTTP/JSON──>  Spring Boot REST API  ──JDBC──>  PostgreSQL
                                             │
                                             └──HTTP──> Python AI/OCR service
                                                        (added in a later step)
```

The backend is a **modular monolith**, not microservices. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the reasoning.

## Repository structure

```
JobLens/
├── backend/          Spring Boot REST API (Java 21, Maven)
├── frontend/         React + TypeScript client (Vite)
├── ai-service/       Placeholder for the future Python OCR/AI service
├── docker/           Local development infrastructure (PostgreSQL)
├── docs/
│   ├── architecture/ Diagrams and deeper architecture notes
│   ├── daily/        Development log, one file per working day
│   └── decisions/    Architecture decision records
├── .github/workflows/ CI pipeline
├── README.md
├── ARCHITECTURE.md
├── ROADMAP.md
├── TODO.md
└── .env.example
```

## Local development

Prerequisites: JDK 21, Node.js 20, Docker (or a PostgreSQL 16 instance).

```bash
cp .env.example .env                 # then set DB_PASSWORD
docker compose -f docker/docker-compose.yml up -d
```

Backend:

```bash
cd backend && mvn spring-boot:run
```

Frontend:

```bash
cd frontend && cp .env.example .env.local && npm install && npm run dev
```

The backend serves `http://localhost:8080`, the frontend `http://localhost:5173`.
Verify the wiring at `http://localhost:8080/actuator/health` and by loading the
home page, which reports whether it can reach `/api/v1/meta`.

### Development environment note

This repository is authored on a locked-down corporate machine where build and
runtime tooling (Maven, Node, Docker, Python) **may not be executed**. Every
command in this repository's documentation is written for a separate,
permitted development/test machine. Nothing here has been compiled, run or
tested locally — see `docs/daily/` for what remains unverified at each step.

## Configuration

No credentials are committed. Both applications are configured through
environment variables:

- Backend: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`,
  `SERVER_PORT`, `CORS_ALLOWED_ORIGINS`, `APP_VERSION`
- Frontend: `VITE_API_BASE_URL`

## Deployment direction

Free-first, verified against current free-tier terms when we actually deploy:

- Source control — GitHub
- Frontend — Vercel
- Backend — Render (or an equivalent free/low-cost provider)
- Database — a managed free-tier PostgreSQL provider
- AI service — free/low-cost hosting once it exists

No paid infrastructure or paid APIs are introduced without explicit approval.

## Beyond V1

V2/V3 ideas are recorded in [ROADMAP.md](ROADMAP.md) — logo/visual recognition
rather than text-only OCR, a mobile client, saved searches with alerts, employer
verification, and richer AI job intelligence. None of this is being built now.

## Verification workflow

Because nothing can be built or run on the authoring machine, CI and the
deployed environments are the only proof that the code works. Every step
follows the same loop — generate, push, CI green, deploy, smoke test — and a
failing gate is fixed before the next step begins.

- The loop and the per-step smoke tests: [docs/VERIFICATION.md](docs/VERIFICATION.md)
- One-time Render and Vercel setup: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)
