# Deployment

Everything below is free and needs no credit card. The day-to-day loop is in
[VERIFICATION.md](VERIFICATION.md).

| Piece    | Provider       | Free-tier shape (verify before relying on it) |
| -------- | -------------- | --------------------------------------------- |
| Frontend | Vercel Hobby   | Static build on a CDN. No sleeping.           |
| Backend  | Render (Docker)| 512 MB RAM, 0.1 CPU, 750 instance hours/month. Sleeps after ~15 min idle, up to a minute to wake. 500 build minutes/month. |
| Database | Neon           | Permanent free tier. ~0.5 GB storage, 100 compute-hours/month, scale-to-zero. Exceeding the limits suspends compute; it does not delete data. |

**Why Neon and not Render PostgreSQL.** Render's free database expires 30 days
after creation — 14-day grace period, then it and its data are deleted. That is
fine for a throwaway test and wrong for a portfolio project that should still be
up when someone looks at it. Neon's free tier has no expiry. Supabase is the
other credible option but pauses a project after a week of inactivity, which is
exactly the traffic pattern a portfolio project has.

Order matters: **database → backend → frontend → CORS**, because each step needs
a value from the one before.

---

## 0. Push to GitHub

CI is the first gate. It runs on pushes to `main` and `master`.

```bash
git push -u origin master
```

Open the **Actions** tab and wait for both jobs to go green before touching any
provider. A compile or type error should never reach a build queue.

> Optional but conventional: rename the branch to `main`
> (`git branch -M main && git push -u origin main`, then change the default
> branch in GitHub → Settings → Branches).

---

## 1. Database — Neon

1. Sign up at [neon.com](https://neon.com) with GitHub. No card.
2. Create a project. Pick the region nearest the Render region you will use
   (`oregon` in `render.yaml` → US West) so queries do not cross a continent.
3. Open **Connection details**. You need five values, which are easiest to read
   off the connection string:

   ```
   postgresql://USERNAME:PASSWORD@HOST/DATABASE?sslmode=require
                 ^^^^^^^^ ^^^^^^^^ ^^^^ ^^^^^^^^
   ```

   | Variable      | Value                                |
   | ------------- | ------------------------------------ |
   | `DB_HOST`     | the host, e.g. `ep-xxx.us-west-2.aws.neon.tech` |
   | `DB_PORT`     | `5432`                               |
   | `DB_NAME`     | usually `neondb`                     |
   | `DB_USERNAME` | usually `neondb_owner`               |
   | `DB_PASSWORD` | the password                         |

   Keep them somewhere private. They do not go in the repository.

Flyway creates the schema on first startup, so there is nothing to run here.

## 2. Backend — Render

1. Sign up at [render.com](https://render.com) with GitHub.
2. **New → Blueprint**, select the repository. Render reads
   [`render.yaml`](../render.yaml) and proposes the `joblens-api` Docker service.
3. It prompts for every `sync: false` variable. Enter the five `DB_*` values
   from Neon. For `CORS_ALLOWED_ORIGINS` put `http://localhost:5173` for now —
   step 4 corrects it.
4. Apply, and watch the build. The first Docker build downloads all the Maven
   dependencies, so expect several minutes; later builds reuse the cached layer.
5. Deploy succeeds when the health check at `/actuator/health` passes. Note the
   service URL, e.g. `https://joblens-api.onrender.com`.

If the blueprint is rejected, create it manually instead: **New → Web Service**,
runtime **Docker**, Dockerfile path `./backend/Dockerfile`, Docker context
`./backend`, plan **Free**, health check path `/actuator/health`, then add all
the environment variables listed in `render.yaml` by hand.

Specific things that matter:

- **Do not set `PORT` or `SERVER_PORT`.** Render injects `PORT` and
  `application.yml` honours it. Setting them yourself breaks the health check.
- **Keep `DB_URL_PARAMS=?sslmode=require`.** Neon refuses plaintext connections.
- **Free services sleep.** The first request after idling takes up to a minute
  and looks like a failure. Retry once before debugging anything.

## 3. Frontend — Vercel

1. Sign up at [vercel.com](https://vercel.com) with GitHub.
2. **Add New → Project**, import the repository.
3. Set **Root Directory** to `frontend`. This is the step people miss in a
   monorepo; without it the build cannot find `package.json`.
4. Framework preset **Vite** — build command `npm run build` and output `dist`
   are inferred.
5. Add an environment variable for **Production and Preview**:

   ```
   VITE_API_BASE_URL = https://joblens-api.onrender.com
   ```

   No trailing slash. It is **inlined at build time**, so changing it later
   requires a redeploy, not just a save. It is also public — visible to anyone
   who reads the JavaScript bundle — which is fine for a base URL and is exactly
   why no secret may ever carry a `VITE_` prefix.
6. Deploy, and note the URL, e.g. `https://joblens.vercel.app`.

[`frontend/vercel.json`](../frontend/vercel.json) rewrites all paths to
`index.html`, which client-side routing needs — without it, reloading a deep
link returns a CDN 404.

## 4. Close the loop — CORS

Back in Render → `joblens-api` → **Environment**, set

```
CORS_ALLOWED_ORIGINS = https://joblens.vercel.app
```

Exact origin: scheme included, no trailing slash, no path. Save; Render
redeploys. Until this is right the frontend gets `Failed to fetch` and the
environment panel reports `API reachable: no`.

Vercel gives every preview deployment its own hostname, so previews will fail
CORS against a production-only allowlist. The variable accepts a comma-separated
list, so add specific preview origins when you need them. We are not adding
wildcard origin matching.

Now run the Step 1 smoke tests in [VERIFICATION.md](VERIFICATION.md).

---

## Testing it for free

Three layers, all free, cheapest first:

1. **GitHub Actions** — compiles the backend, runs its tests against a real
   PostgreSQL service container, type-checks and builds the frontend. Free for
   public repositories, and the fastest feedback available on a machine that
   cannot run anything.
2. **The deployed environments** — the smoke tests in `VERIFICATION.md`:
   `curl` against the API, the Flyway history query in Neon's SQL editor, and
   the environment-check panel on the home page.
3. **A permitted machine, if you ever get one** — `docker compose` plus
   `mvn verify` and `npm run dev`, per the README. Optional; the loop works
   without it.

## Keeping it inside the free limits

- 750 instance hours/month covers exactly one always-eligible free service.
  Do not run a second one in the same month.
- Do not add an uptime pinger to stop Render sleeping. It burns instance hours
  and Neon compute-hours to solve a problem that costs one slow request.
- Pushes to `main`/`master` trigger a Render build and a Vercel build. Batch
  small commits when you are near the 500 build-minute limit.
- Neon suspends compute when the budget is exhausted but keeps the data, so the
  failure mode is a refused connection, not data loss.

## Cost

Zero, on all three providers, with no card. The risk is not a bill; it is a
provider changing its free tier. That is why the backend ships as a plain
Dockerfile and the database is reached purely through environment variables:
moving to Railway, Fly.io or Koyeb means new hosting instructions, not new code.

## CI/CD

GitHub Actions currently only builds and tests; it does not deploy. Render and
Vercel both watch the repository and deploy on push themselves, which is enough
for now. Gating deployment behind a green CI run belongs to the deployment step
of the roadmap.
