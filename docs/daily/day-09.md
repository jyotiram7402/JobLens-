# Day 09 - Dashboard and the company/job UI

**Date:** 2026-10-08
**Roadmap step:** 9 - Dashboard + Company/Job UI

## Objective

Connect the Step 8 frontend to the backend and build the first UI that behaves
like a product: a dashboard, job search with working filters, job and company
detail, and the match breakdown.

## Two things added beyond the brief, and why

**Authentication was connected.** Step 8 left login and register as markup. The
dashboard needs the user's name, and both `/jobs/recommended` and
`/jobs/{id}/match` return 401 without a token — so without this, most of Step 9
would have been undeliverable. `AuthContext`, the two forms and the route guard
are the minimum to make the rest real.

**Profile editing was implemented.** Three separate places in this step tell the
user to go and complete their profile. With `ProfilePage` still a placeholder,
every one of those was a dead end, and a new account could never produce a match
score at all — which would have made the match display untestable.

Both are prerequisites of Step 9's own features rather than new scope, but they
are additions and worth stating.

## Corrections to the brief

The backend was read rather than assumed, which caught three things:

- **There is no `GET /companies/{id}/jobs`.** A company's openings come from
  `/jobs?companyId=…`. Using the ordinary search means the list inherits
  pagination and every other filter rather than being a second code path.
- **The job field is `applyUrl`, not `applicationUrl`**, and there is **no
  `source` field** — so the job detail page does not claim to show one.
- `/jobs/recommended` is a paginated list of `RecommendedJob`, which wraps a
  `JobSummary` plus a score and a short explanation — not a list of jobs.

## UI architecture

```
React UI
   │
   ├── Dashboard
   ├── Job Search
   ├── Job Details
   └── Company Details
          │
          ▼
     Feature API        features/*/api.ts
          │
          ▼
     Shared API Client  services/api/client.ts
          │
          ▼
     Spring Boot API
```

No component calls the HTTP client directly. Each feature's `api.ts` is the only
thing that knows which endpoint serves it.

## Pages

| Route | Auth | What it does |
| ----- | ---- | ------------ |
| `/dashboard` | required | Greeting, four statistic tiles, recommendations, recent jobs |
| `/jobs` | public | Search, five filters, sort, pagination |
| `/jobs/:jobId` | public | Full job, apply link, match panel |
| `/companies/:companyId` | public | Company, links, open positions |
| `/profile` | required | The fields matching scores against |
| `/login`, `/register` | public | Connected to the API |

Route protection mirrors the backend's own rules rather than inventing stricter
ones: browsing is public, because the backend serves those endpoints to
anonymous callers and discovery is the product.

## API integrations

| Endpoint | Used by |
| -------- | ------- |
| `POST /auth/register`, `POST /auth/login` | Registration, sign-in |
| `GET /users/me` | `AuthContext`, on load and after a profile save |
| `PUT /users/me/profile` | Profile page |
| `GET /jobs` | Job search, dashboard recent jobs, company openings |
| `GET /jobs/{id}` | Job detail |
| `GET /jobs/recommended` | Dashboard |
| `GET /jobs/{id}/match` | Match panel |
| `GET /companies/{id}` | Company detail |

## Search and filter UX

**The URL is the state.** `?search=java&location=Pune&workMode=HYBRID&page=1` is
refreshable, shareable and works with the Back button. Keeping the same values
in component state as well would mean two sources of truth that drift as soon as
someone edits the address bar.

The keyword box is the one exception: it holds a local value so typing stays
responsive, and writes to the URL after a 300 ms debounce. Pushing every
keystroke into the URL would fill the history with half-typed words and fire a
request per character.

Filters apply on change — they are selects and one short text field, so a change
is a deliberate act rather than a stream of keystrokes, and an Apply button
would be a second step for no benefit. Any change other than paging resets to
page 0, because staying on page 4 while narrowing a search is how a user lands
on an empty page and concludes there are no results.

Experience is five named bands rather than a slider. Two numeric inputs or a
dual-handle control are both fiddlier and harder to make keyboard-accessible for
a question most people answer approximately. Each band maps to the
`experienceMin` the backend already understands — and because matching is a
range *overlap*, "3+ years" correctly returns jobs asking for 1-5 as well as 5+.

Sort options are fixed in code, not taken from user input: the backend rejects
anything outside its allowlist with a 400.

All filtering is backend filtering. Nothing fetches a large list and narrows it
in the browser.

## Match display

Score, a per-criterion table, matched and missing skills, and the backend's own
explanation sentences. Nothing is generated in React — the prose comes from the
same comparison the score did, which is what stops the two disagreeing.

An inapplicable criterion renders as **"not compared"**, not `0/50`. The backend
excluded it from the total along with its weight; a zero would read as a failure
the user did not have.

`MatchPanel` fetches its own data and degrades on its own, so matching — the
part most likely to be unavailable — never takes the job page with it:

| Situation | What is shown |
| --------- | ------------- |
| Not signed in | An invitation to sign in |
| `422 PROFILE_NOT_READY` | "Complete your profile", linked |
| `scored: false` | The backend's explanation, and the same link |
| Any other failure | "Unavailable right now. The rest of this page still works." |

Never a placeholder percentage. A fake score is worse than no score, because the
user would act on it.

## Dashboard, and the honesty rule

Every figure is from the API or derived from data already on the page:

| Tile | Source |
| ---- | ------ |
| Recommended jobs | `totalElements` from `/jobs/recommended` |
| Strong matches | Counted over the loaded recommendations, labelled "in your top 5" |
| Profile completion | Derived from the six profile fields matching uses |
| Tracked companies | "Coming soon" |

Profile completion counts the six fields that change a score, not every field on
the profile — a percentage driven by a summary paragraph nobody matches on would
mean nothing. The tile names the missing fields, which maps directly onto the
criteria matching reports as "not compared", and a prompt below links to the
profile.

Tracked companies says "Coming soon" rather than 0. A zero reads as a fact about
the user's data; the feature simply does not exist, and one invented number
makes every other number on the page untrustworthy.

## Loading, error and empty states

All three on every data-backed view, through `useAsync` and the shared
components.

Empty is not an error: an unmatched search is a *successful* request and the
backend returns 200 with an empty array for exactly that reason. Error states
show the `traceId` — the same value on every backend log line for that request —
and offer a retry. Neither renders a raw server message for a 5xx.

`useAsync` aborts on unmount and ignores stale responses. Without the latter,
typing a search quickly lets an earlier, slower response overwrite a later one,
and the list shows results for a query the user has already changed.

## Responsive design

- Layout: one column below 48rem, sidebar and content above it. The navigation
  becomes a toggled menu with `aria-expanded` and `aria-controls`.
- Filters: `auto-fit` grid, so five columns reflow to one without a breakpoint
  per layout.
- Job detail: single column below 60rem; above it the apply and match panels sit
  beside the description, because they are the actions.
- No tables for basic information — the only `<table>` is the match breakdown,
  which genuinely is tabular and has two narrow columns.

## Accessibility

- One `<h1>` per page, via `PageHeader`.
- `JobCard` is an `<article>` with the title as the only link. A whole-card link
  would swallow future controls and announce the entire card as its name.
- Job facts are a `<dl>` with visually hidden `<dt>`s: sighted users read the
  structure from the separators, screen reader users hear the labels.
- Match score always renders the number and the word "match". Colour is
  reinforcement, never the message.
- Matched and missing skills carry hidden "Matched:" / "Missing:" text, so the
  ✓ and + symbols are decorative rather than load-bearing.
- Results region is `aria-live="polite"` with `aria-busy`, so a silently
  replaced list is announced.
- Pagination position is `aria-live`; the nav is labelled.
- External links announce "(opens in a new tab)".
- Save confirmation is `role="status"`; errors are `role="alert"`.

## Files created

```
frontend/src/hooks/{useAsync.ts,useDebouncedValue.ts}
frontend/src/utils/format.ts
frontend/src/features/auth/{api.ts,AuthContext.tsx}
frontend/src/features/jobs/api.ts
frontend/src/features/jobs/components/{JobCard.tsx,JobFilters.tsx}
frontend/src/features/companies/api.ts
frontend/src/features/matching/api.ts
frontend/src/features/matching/components/{MatchPanel.tsx,MatchScore.tsx}
frontend/src/features/profile/api.ts
frontend/src/components/ui/{Avatar.tsx,Pagination.tsx}
frontend/src/styles/product.css
frontend/src/test/render.tsx
frontend/src/features/jobs/components/JobCard.test.tsx
frontend/src/features/jobs/pages/{JobsPage,JobDetailPage}.test.tsx
frontend/src/features/companies/pages/CompanyDetailPage.test.tsx
frontend/src/features/dashboard/pages/DashboardPage.test.tsx
docs/daily/day-09.md
```

## Files modified

```
frontend/src/app/{App.tsx,router.tsx}        AuthProvider, RequireAuth
frontend/src/components/layout/{AppLayout,AppNav}.tsx  user menu, mobile toggle
frontend/src/components/ui/index.ts          Avatar, Pagination
frontend/src/features/auth/pages/*.tsx       connected to the API
frontend/src/features/dashboard/pages/DashboardPage.tsx
frontend/src/features/jobs/pages/{JobsPage,JobDetailPage}.tsx
frontend/src/features/companies/pages/CompanyDetailPage.tsx
frontend/src/features/profile/pages/ProfilePage.tsx
frontend/src/main.tsx                        imports product.css
README.md, ARCHITECTURE.md, ROADMAP.md, TODO.md, docs/VERIFICATION.md
```

## Tests created

| File | Covers |
| ---- | ------ |
| `JobCard.test.tsx` | Title, company, location, work mode, type, experience; links to job and company; relative posting date; match score shown when supplied and absent when not; "Not scored" rather than 0%; experience omitted when unstated; skills only when provided |
| `JobsPage.test.tsx` | Search box and all five filters render; loading state; results listed; **filters from the URL reach the backend**; empty state is not an error; error state with trace id and retry; pagination hidden for one page and shown for several |
| `JobDetailPage.test.tsx` | Job details; apply link with `rel="noopener noreferrer"` and `target="_blank"`; 404 handled; anonymous visitor invited to sign in and no match request made; score, breakdown and explanation; "not compared" for an inapplicable criterion; 422 asks for a profile and shows no percentage; **the job stays readable when matching fails** |
| `CompanyDetailPage.test.tsx` | Company information and safe external links; generated initials with no logo; openings listed; **fetched via job search filtered by company**; empty state; 404 handled; company still shown when its openings fail |
| `DashboardPage.test.tsx` | Greeting uses the real name; recommendations with scores; recent jobs and the view-all link; profile completion derived from the six matching fields; tracked companies says "Coming soon" rather than 0; 422 asks for a profile; empty state; a recommendations failure does not break the rest |

Tests mock the feature API modules rather than `fetch`, because they are about
what a page does with a result; how a request is built is covered by
`ApiError.test.ts`.

## Commands for the separate test machine

```bash
cd backend && mvn spring-boot:run
```

```bash
cd frontend && cp .env.example .env.local && npm install
```
**Commit the `package-lock.json`** — it still does not exist.

```bash
npm run typecheck && npm test && npm run build && npm run dev
```

Then, with seed data loaded (the dev profile loads it):

1. Register at `/register` — it should sign you in and land on the dashboard.
2. The dashboard should greet you by name, show "No recommendations yet" with a
   Complete profile link, and show profile completion well under 100%.
3. Fill in the profile with skills that overlap the seeded jobs — Java, Spring
   Boot, Docker — plus 3 years, Pune, and Hybrid.
4. Return to the dashboard: recommendations should appear with real scores, and
   completion should rise.
5. Open a job: the match panel should show the breakdown, the matched and
   missing skills, and the backend's explanation.
6. Search `/jobs?search=java`, add filters, and check the URL updates; refresh
   the page and confirm the filters survive; press Back.
7. Sign out and open the same job: it should still render, with the match panel
   inviting you to sign in.
8. Narrow the window to phone width and check the navigation toggle.

## Commands were NOT executed

**Not executed because this is the locked-down office machine:** `npm`, `node`,
`npx`, `vite`, `vitest`, `tsc`, `java`, `mvn`, `docker`, `python`. Nothing here
has been installed, type-checked, built, run or tested. No claim is made that
the frontend compiles, that any test passes, or that any page renders.

## Things that could not be verified

- **That it type-checks.** This is by far the largest amount of TypeScript
  written in one step, against `noUnusedLocals`, `noUnusedParameters` and
  `verbatimModuleSyntax`. The likeliest failure.
- **`vi.mocked` on the feature modules.** The mock factories must match each
  module's exports exactly or the import fails at run time rather than with a
  helpful message.
- **The `useAsync` dependency arrays.** `JobsPage` depends on
  `searchParams.toString()`, which is correct but unusual; if it is wrong the
  symptom is a request loop rather than a stale page, so watch the network tab.
- **`renderWithProviders` and `AuthContextForTests`.** Supplying the context
  directly bypasses `AuthProvider`; if the export is wrong every page test
  fails together.
- **The debounce behaviour under test.** No test covers the keyword debounce,
  because it needs fake timers and the interaction with `useSearchParams` is
  fiddly. It is covered by the manual checklist instead, which is an honest gap.
- **Anything about appearance.** No page has been rendered. The responsive
  breakpoints, the dark mode palette and the match table are written but unseen.
- **Still nothing on the backend side.** Steps 1-9 have never been through CI or
  a deployment, so none of these integrations has ever made a real request.

## Next step

Step 10 — Tracking: follow companies and jobs, application status per tracked
job, tracking views, and the dashboard's "Tracked companies" tile made real.

Not started.
