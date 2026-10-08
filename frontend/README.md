# JobLens frontend

React 18 + TypeScript + Vite, talking to the Spring Boot API in `../backend`.

## Running it

```bash
cp .env.example .env.local
npm install
npm run dev
```

The dev server runs on `http://localhost:5173`, which is the origin the backend
allows by default in its `dev` profile.

| Script | What it does |
| ------ | ------------ |
| `npm run dev` | Vite dev server with hot reload |
| `npm run build` | Type-checks, then builds to `dist/` |
| `npm run typecheck` | Types only, no build |
| `npm test` | Vitest, once |
| `npm run test:watch` | Vitest, watching |

## Structure

```
src/
├── app/           root component, router, error boundary, non-feature pages
├── components/
│   ├── layout/    the application shell
│   └── ui/        shared building blocks (Button, Input, states…)
├── features/      one folder per domain, each owning its pages and types
│   ├── auth/  companies/  dashboard/  jobs/  matching/  profile/  scan/
├── lib/           environment configuration
├── services/
│   ├── api/       HTTP client, error type, endpoint paths
│   └── auth/      token storage
├── types/         shapes shared across features (PageResponse, ApiErrorBody)
└── test/          Vitest setup
```

Feature code lives with its feature. `components/` is for things used by more
than one of them — a component used once belongs with its page.

## Talking to the backend

Every request goes through `services/api/client.ts`, which adds the base URL,
the `Authorization` header and error translation. Paths live in
`services/api/endpoints.ts` rather than as string literals.

```ts
import { api } from '../services/api/client';
import { endpoints } from '../services/api/endpoints';
import type { PageResponse } from '../types/api';
import type { JobSummary } from './types';

const page = await api.get<PageResponse<JobSummary>>(endpoints.jobs.search, {
  query: { search: 'java', location: 'Pune', page: 0, size: 20 },
});
```

Failures throw `ApiError`, which carries `status`, a stable `code`, a `message`,
field-level `details` for a 400, and the `traceId` that also appears in the
backend logs.

## Environment variables

| Variable | Notes |
| -------- | ----- |
| `VITE_API_BASE_URL` | Backend **origin**, e.g. `http://localhost:8080`. Not the API root — `/api/v1` is added in code. |

Vite inlines `VITE_*` at build time, so a change needs a redeploy, and anything
behind that prefix is public. No secrets.

## Deploying

Vercel, with **Root Directory set to `frontend`** — the step people miss in a
monorepo. Framework preset Vite; build command and output directory are
inferred. Set `VITE_API_BASE_URL` for Production and Preview.

`vercel.json` rewrites every path to `index.html`, which client-side routing
needs: without it, reloading `/jobs/123` returns a 404 from the CDN.

See [../docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md).

## Note

Nothing here has been run. The project is authored on a machine where `npm` may
not be executed; CI and the deployed environments are the verification.
