# Docker

## What lives here

- `docker-compose.yml` — local development infrastructure. Right now that is a
  single PostgreSQL 16 container.

The application itself is **not** containerised yet. During development the
backend runs via `mvn spring-boot:run` and the frontend via `npm run dev`, which
is faster than rebuilding images on every change. Application Dockerfiles are
added in the "Production readiness" step of the roadmap, when we know what the
deployment target actually needs.

## Usage

On a machine where Docker is permitted, from the repository root:

```bash
cp .env.example .env          # then edit DB_PASSWORD
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

To stop, keeping the data volume:

```bash
docker compose -f docker/docker-compose.yml down
```

To stop and delete the database volume:

```bash
docker compose -f docker/docker-compose.yml down -v
```

## Test database

The backend test suite uses the `test` profile and expects a database named
`joblens_test`. Create it once against the running container:

```bash
docker exec -it joblens-postgres createdb -U joblens joblens_test
```

## Note

These commands have **not** been executed. The project is authored on a
locked-down office machine where Docker and other build tooling may not be run.
