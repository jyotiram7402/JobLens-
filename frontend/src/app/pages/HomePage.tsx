import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { config } from '../../lib/config';
import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import { ApiError } from '../../services/api/ApiError';
import { Card } from '../../components/ui';

interface ApiMeta {
  application: string;
  version: string;
  environment: string;
}

type ConnectionState =
  | { status: 'loading' }
  | { status: 'ok'; meta: ApiMeta }
  | { status: 'error'; message: string };

/**
 * The public landing page.
 *
 * <p>It also carries the environment check kept from step 1, which is the
 * fastest way to tell whether a freshly deployed frontend can actually reach
 * its backend. On a machine where nothing can be run locally, a page that
 * diagnoses its own deployment is worth more than a prettier hero section.
 */
export function HomePage() {
  const [connection, setConnection] = useState<ConnectionState>({ status: 'loading' });

  useEffect(() => {
    const controller = new AbortController();

    api
      .get<ApiMeta>(endpoints.meta, { signal: controller.signal })
      .then((meta) => setConnection({ status: 'ok', meta }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        setConnection({
          status: 'error',
          message: error instanceof ApiError ? error.message : 'Unknown error',
        });
      });

    // Cancels the request if the page unmounts first, so no state is set on a
    // component that is already gone.
    return () => controller.abort();
  }, []);

  return (
    <div className="stack">
      <section className="hero">
        <h1 className="hero-title">See a company. Discover the opportunity.</h1>
        <p className="hero-text">
          Point your camera at a company sign and JobLens identifies the company, surfaces its
          public job openings, and scores each one against your profile — with the reasons, not
          just a number.
        </p>
        <div className="hero-actions">
          <Link className="btn btn-primary" to="/register">
            Create an account
          </Link>
          <Link className="btn btn-secondary" to="/jobs">
            Browse jobs
          </Link>
        </div>
      </section>

      <Card>
        <h2 className="card-title">Environment check</h2>
        <dl className="env-check">
          <dt>API base URL</dt>
          <dd>
            {config.isApiConfigured ? <code>{config.apiBaseUrl}</code> : <em>not configured</em>}
          </dd>

          <dt>API reachable</dt>
          <dd>
            {connection.status === 'loading' && 'checking…'}
            {connection.status === 'ok' && (
              <>
                yes — <strong>{connection.meta.application}</strong> {connection.meta.version} (
                {connection.meta.environment})
              </>
            )}
            {connection.status === 'error' && <span role="alert">no</span>}
          </dd>
        </dl>

        {connection.status === 'error' && (
          <p className="state-detail" role="alert">
            {connection.message}
          </p>
        )}
      </Card>
    </div>
  );
}
