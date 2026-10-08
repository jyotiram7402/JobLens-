# Day 08 - React frontend foundation

**Date:** 2026-10-08
**Roadmap step:** 8 - Frontend foundation

## Objective

Turn the step 1 React skeleton into a frontend architecture the remaining UI
work can be built into: feature-oriented structure, routing, an API client,
TypeScript models mirroring the real backend DTOs, shared accessible
components, and a testing setup.

Architecture and shells only. No page is connected to the API except the home
page's environment check.

## Existing implementation reviewed

Step 1 left a working but minimal Vite + React + TypeScript project:

- `package.json` with React 18, React Router 6, Vite 5, TypeScript 5 — all kept,
  no versions changed.
- `tsconfig.json` already strict, with `noUnusedLocals`, `noUnusedParameters`
  and `verbatimModuleSyntax`. Unchanged.
- `vite.config.ts`, `index.html`, `vercel.json` — kept; only `vite.config.ts`
  gained a `test` block.
- `src/lib/config.ts` and `src/lib/api.ts` from step 1b, which already handled a
  missing `VITE_API_BASE_URL` without blanking the page, and distinguished a
  network failure from an HTTP error. Both ideas were carried forward rather
  than rediscovered: `config.ts` was extended, and `api.ts` grew into
  `services/api/`.
- `HomePage` with its environment check, which is the fastest way to tell
  whether a deployment can reach its backend. Kept and expanded.

Backend DTOs were read directly rather than assumed, which caught two things
the brief had wrong:

- The job field is **`applyUrl`**, not `applicationUrl`, and there is no
  `source` field.
- There is **no `/companies/{id}/jobs` endpoint.** A company's openings come
  from `/jobs?companyId=…`.

## Frontend architecture

```
React
  │
  ├── Router            app/router.tsx — public and protected groups
  ├── Feature modules   features/*, each owning its pages and types
  ├── Shared components components/{layout,ui}
  └── API services      services/api
          │
          ▼
     Spring Boot
```

## Folder structure

```
frontend/src/
├── app/
│   ├── App.tsx              root: error boundary + router
│   ├── router.tsx           the route table
│   ├── providers/ErrorBoundary.tsx
│   └── pages/{HomePage,NotFoundPage}.tsx
├── components/
│   ├── layout/{AppLayout,AppNav,PageHeader}.tsx
│   └── ui/{Button,Input,Select,Card,Badge,Spinner,
│           LoadingState,ErrorState,EmptyState}.tsx
├── features/
│   ├── auth/{pages/{Login,Register}Page.tsx, types.ts}
│   ├── companies/{pages/CompanyDetailPage.tsx, types.ts}
│   ├── dashboard/pages/DashboardPage.tsx
│   ├── jobs/{pages/{Jobs,JobDetail}Page.tsx, types.ts}
│   ├── matching/types.ts
│   ├── profile/{pages/ProfilePage.tsx, types.ts}
│   └── scan/pages/ScanPage.tsx
├── lib/config.ts
├── services/
│   ├── api/{client,ApiError,endpoints}.ts
│   └── auth/tokenStorage.ts
├── types/api.ts
├── test/setup.ts
├── main.tsx
└── index.css
```

Feature-oriented for the same reason the backend is package-per-domain: a change
to job search touches `features/jobs/` rather than being spread across a
`components/`, a `pages/` and a `types/` directory. Something is promoted into
`components/` when a second feature needs it — a component used once belongs
with its page.

No empty folders. `features/matching/` has types but no page, because the match
breakdown will render inside the job detail page rather than on its own route.

## Routing

| Group | Routes |
| ----- | ------ |
| Public | `/`, `/login`, `/register` |
| Protected | `/dashboard`, `/jobs`, `/jobs/:jobId`, `/companies/:companyId`, `/profile`, `/scan` |
| Fallback | `*` |

Everything is nested under one layout, so the shell is defined once.

The protected group sits under a `ProtectedRoutes` element that currently
renders its children unchanged. The guard is one line and this is where it goes,
but adding it now would lock everyone out of the only pages that could test it —
sign-in is not connected. The grouping makes "which pages need an account?"
answerable from the route table.

**It is a convenience, not a security boundary.** The backend rejects
unauthenticated requests itself; hiding a route in the browser stops nothing.

Documented but not routed: `/tracked-companies` (step 10) and
`/jobs/recommended` (step 9). The second must be declared *before*
`/jobs/:jobId`, or "recommended" is parsed as an id.

## API configuration

`VITE_API_BASE_URL` is the backend **origin** (`http://localhost:8080`), not the
API root. The `/api/v1` prefix lives in `lib/config.ts`.

That is a deliberate departure from the brief's example. The version belongs to
the API contract rather than to the deployment, so moving to `/api/v2` becomes a
code change instead of a reconfiguration of every environment — and it keeps the
variable's meaning identical to what `docs/DEPLOYMENT.md` and `render.yaml`
already document, so nothing had to be re-explained.

A missing value still does not throw at module load. It is reported, and the
home page says so.

## TypeScript types

Mirrored field for field from the backend DTOs:

| File | Types |
| ---- | ----- |
| `types/api.ts` | `PageResponse<T>`, `ApiErrorBody`, `PageParams` |
| `features/auth/types.ts` | `User`, `RegisterRequest`, `RegistrationResponse`, `LoginRequest`, `AuthResponse` |
| `features/profile/types.ts` | `UserProfile`, `CurrentUser`, `UpdateUserProfileRequest`, `RemotePreference` |
| `features/companies/types.ts` | `Company`, `CompanySummary`, `CompanySearchParams` |
| `features/jobs/types.ts` | `Job`, `JobSummary`, `JobCompanyRef`, `EmploymentType`, `WorkMode`, `JobSearchParams`, display labels |
| `features/matching/types.ts` | `JobMatch`, `CriterionScore`, `RecommendedJob`, `MatchCriterionKey`, labels |

They describe the contract, not the backend's internals: normalized names and
entity versions are absent because the API does not expose them.

`CriterionScore.applicable: false` is documented in the type itself, because it
is the easiest thing for UI code to get wrong — it means the criterion was
excluded from the total along with its weight, not that it scored zero. The
`maxScore` is 0, and the UI must show the reason rather than a 0/50 bar.

## Shared components

Button, Input, Select, Card, Badge, Spinner, LoadingState, ErrorState,
EmptyState. Nine, and that is meant to be the whole set for now.

Accessibility is part of each one rather than a later pass: `Button` renders a
real `<button>` and defaults `type="button"` so it cannot accidentally submit a
form; `Input` requires a label and ties it with `useId`, setting `aria-invalid`
and `aria-describedby` on errors; `Select` stays native, because reimplementing
keyboard navigation, typeahead and the mobile picker is a lot of work to look
slightly different; `Badge` takes an optional screen-reader label so colour is
never the only signal; the spinner respects `prefers-reduced-motion`.

`ErrorState` and `EmptyState` are separate on purpose. A search that matched
nothing is a *successful* request — the backend returns 200 with an empty array
— and showing an error would misreport what happened.

## State management

React state and Context. No Redux, no Zustand, no store.

Nothing is currently shared between distant parts of the tree. The one candidate
is the signed-in user, which is a small Context when auth is wired up. A store
adopted before there is shared state to put in it is a set of conventions
without a problem, and far easier to add later than to unpick.

No data-fetching library either. TanStack Query brings caching, retries and
deduplication, none of which this application needs — there is no page that
fetches the same thing twice. It can wrap the client later without feature code
changing.

## Vercel considerations

Unchanged from what step 1b already set up and `docs/DEPLOYMENT.md` documents:
Root Directory `frontend`, Vite preset, `vercel.json` rewriting all paths to
`index.html` so a reload of `/jobs/123` is not a CDN 404, `VITE_API_BASE_URL`
set for Production and Preview, and no hard-coded backend URL anywhere.

Nothing was deployed.

## Files created

```
frontend/src/app/{App.tsx,router.tsx}
frontend/src/app/providers/ErrorBoundary.tsx
frontend/src/app/pages/{HomePage.tsx,NotFoundPage.tsx,HomePage.test.tsx}
frontend/src/components/layout/{AppLayout,AppNav,PageHeader}.tsx
frontend/src/components/ui/{Button,Input,Select,Card,Badge,Spinner,
    LoadingState,ErrorState,EmptyState}.tsx + index.ts
frontend/src/components/ui/{Button,Input}.test.tsx
frontend/src/features/*/pages/*.tsx        (8 page shells)
frontend/src/features/*/types.ts           (5 type modules)
frontend/src/services/api/{client,ApiError,endpoints}.ts
frontend/src/services/api/ApiError.test.ts
frontend/src/services/auth/tokenStorage.ts
frontend/src/types/api.ts
frontend/src/test/setup.ts
frontend/src/index.css
frontend/README.md
docs/daily/day-08.md
```

## Files modified

```
frontend/package.json        vitest, jsdom, Testing Library; test scripts
frontend/vite.config.ts      Vitest configuration
frontend/.env.example        documents origin-not-API-root, and the VITE_ warning
frontend/src/main.tsx        renders <App/>, imports index.css
frontend/src/lib/config.ts   apiUrl(), apiVersionPath
.github/workflows/ci.yml     runs npm test
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md
```

## Files removed

```
frontend/src/routes/router.tsx        -> app/router.tsx
frontend/src/pages/{Home,NotFound}Page.tsx -> app/pages/
frontend/src/lib/api.ts               -> services/api/
frontend/src/styles.css               -> index.css
```

## Tests created

| File | Covers |
| ---- | ------ |
| `Button.test.tsx` | Renders a real button with an accessible name; defaults to `type="button"`; disabled and `aria-busy` while loading; does not fire while loading |
| `Input.test.tsx` | Label is genuinely associated (found by label text); `aria-invalid` and an announced error; not marked invalid without one; hint becomes the accessible description |
| `HomePage.test.tsx` | Exactly one `<h1>`; the two entry-point links; an unreachable API shows an alert on the page rather than only in the console. Stubs `fetch` rather than the client, so the real client and error translation are exercised |
| `ApiError.test.ts` | Reads the backend error shape including `details` and `traceId`; falls back when the body is HTML rather than JSON; classifies 401/404/network |

Vitest with jsdom and Testing Library — the lightweight option that shares
Vite's config and transform pipeline. Assertions go through roles and labels, so
a passing test also means assistive technology can see the thing.

## Commands for the separate test machine

```bash
cd frontend
cp .env.example .env.local
npm install
```

**Commit the resulting `package-lock.json`** — it still does not exist, and CI
uses `npm install` rather than `npm ci` only because of that.

```bash
npm run typecheck
npm test
npm run build
npm run dev
```

With the backend running on 8080, open `http://localhost:5173` and check:

- the environment check reports `API reachable: yes` with the application name,
  version and environment;
- the navigation links reach every placeholder page;
- `/jobs/abc` and `/companies/abc` render and echo the route parameter;
- an unknown path renders the not-found page;
- tab through the home page: the skip link appears on first Tab, and every
  control shows a focus ring;
- narrow the window to phone width — the navigation moves above the content and
  nothing scrolls horizontally.

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `npm`, `node`,
`npx`, `vite`, `vitest`, `tsc`, `java`, `mvn`, `docker`, `python`. Nothing here
has been installed, type-checked, built, run or tested. No claim is made that
the frontend compiles, that any test passes, or that Vercel would deploy it.

## Things that could not be verified

- **That it type-checks.** `tsconfig.json` has `noUnusedLocals`,
  `noUnusedParameters` and `verbatimModuleSyntax` on, which are exactly the
  settings that turn a stray import into a build failure. This is the most
  likely CI failure.
- **That the new devDependencies resolve** — Vitest 2, Testing Library 16,
  jsdom 25 were chosen from knowledge, not from a registry, and
  `@testing-library/react` 16 has peer requirements on React and
  `@testing-library/dom` that may need an explicit entry.
- **That `/// <reference types="vitest/config" />` is the right form** for
  Vitest 2 — it moved from `vitest` to `vitest/config` between versions, and the
  wrong one is a type error on `defineConfig`.
- **That `toHaveAccessibleDescription` exists** in the installed jest-dom.
- **That the layout behaves at phone width.** The CSS is written mobile-first
  but has never been rendered.
- **Nothing about the backend is any more verified than before.** Steps 1-7 have
  still never been through CI or a deployment, so the home page's environment
  check has never once succeeded.

## Next step

Step 9 — Frontend features: connect authentication and add the guard, then job
search, job and company detail with the match breakdown, profile editing, and
the dashboard.

Not started.
