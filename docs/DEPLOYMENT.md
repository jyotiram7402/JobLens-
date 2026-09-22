# Deployment

One-time setup for the two hosted environments. The day-to-day loop is in
[VERIFICATION.md](VERIFICATION.md).

> **Verify the free tiers first.** Everything below reflects how these providers
> worked when this was written, not a guarantee. Before creating anything,
> confirm that Render still offers free web services and free PostgreSQL, and
> what the current limits are. If they do not, the backend `Dockerfile` runs
> anywhere — Railway, Fly.io and Koyeb are the obvious alternatives to price up,
> and only the hosting instructions here would change.

## Backend + database — Render

Option A, from the blueprint (preferred, because it is committed):

1. Render dashboard → **New → Blueprint**, point it at the GitHub repository.
2. It reads [`render.yaml`](../render.yaml) and proposes `joblens-api` (Docker
   web service) and `joblens-db` (PostgreSQL).
3. `CORS_ALLOWED_ORIGINS` is marked `sync: false`, so Render will ask for it.
   Leave it as `http://localhost:5173` for now and correct it once the Vercel
   URL exists.
4. Apply. The database is created first; the service picks up its host, port,
   name, user and password automatically.

Option B, manually: create a PostgreSQL instance, then a web service with
runtime **Docker**, Dockerfile path `./backend/Dockerfile`, Docker context
`./backend`, health check path `/actuator/health`, and set `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` and `CORS_ALLOWED_ORIGINS` by hand.

Things that specifically matter here:

- **Do not set `PORT` or `SERVER_PORT`.** Render injects `PORT`, and
  `application.yml` already honours it.
- **Use the database's internal host.** External connections need SSL; if you
  must use the external host, also set `DB_URL_PARAMS=?sslmode=require`.
- **Free services sleep.** The first request after idling can take tens of
  seconds and may look like a failure. Retry before debugging.
- **Free databases expire.** When one does, recreate it and redeploy — Flyway
  reapplies the migrations from scratch. Never store anything in the database
  that is not reproducible from a migration or a seed script.
- Flyway runs automatically at startup, so there is no separate migration step.

## Frontend — Vercel

1. Vercel → **Add New → Project**, import the repository.
2. Set **Root Directory** to `frontend`. This is the step people miss in a
   monorepo; without it the build cannot find `package.json`.
3. Framework preset **Vite**. Build command `npm run build`, output `dist` —
   Vercel infers both.
4. Add an environment variable, for Production *and* Preview:
   `VITE_API_BASE_URL = https://<your-render-service>.onrender.com`
   (no trailing slash).
5. Deploy.

[`frontend/vercel.json`](../frontend/vercel.json) rewrites all paths to
`index.html`, which client-side routing needs — without it, reloading a deep
link returns a 404 from the CDN.

`VITE_API_BASE_URL` is **baked into the bundle at build time**. Changing it in
the dashboard has no effect until you redeploy. It is also public, visible to
anyone who reads the JavaScript — which is fine for an API base URL and is
exactly why no secret may ever be given a `VITE_` prefix.

## Closing the loop between them

Once both exist, set Render's `CORS_ALLOWED_ORIGINS` to the exact Vercel
production origin (for example `https://joblens.vercel.app`) — scheme included,
no trailing slash — and let the service redeploy.

Vercel gives every preview deployment its own hostname, so previews will fail
CORS against a production-only allowlist. `CORS_ALLOWED_ORIGINS` accepts a
comma-separated list, so add specific preview origins when you need them. We are
not adding wildcard origin matching.

## CI/CD

GitHub Actions currently only builds and tests; it does not deploy. Render and
Vercel both watch the repository and deploy on push to `main` themselves, which
is enough for now. Wiring deployment behind a green CI run belongs to the
deployment step of the roadmap.
