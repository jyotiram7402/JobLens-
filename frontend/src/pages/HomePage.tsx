import { useEffect, useState } from 'react';
import { config } from '../lib/config';
import { getApiMeta, type ApiMeta } from '../lib/api';

type ConnectionState =
  | { status: 'loading' }
  | { status: 'ok'; meta: ApiMeta }
  | { status: 'error'; message: string };

/**
 * Landing page. For now it does one useful thing: prove this deployment is
 * correctly configured and can actually reach its backend. That makes it the
 * smoke test for every environment we deploy to.
 */
export function HomePage() {
  const [connection, setConnection] = useState<ConnectionState>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;

    getApiMeta()
      .then((meta) => {
        if (!cancelled) setConnection({ status: 'ok', meta });
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setConnection({
            status: 'error',
            message: error instanceof Error ? error.message : 'Unknown error',
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section>
      <h1>Welcome to JobLens</h1>
      <p>
        Point your camera at a company sign, and JobLens identifies the company and surfaces its
        public job openings, matched against your profile.
      </p>

      <h2>Environment check</h2>
      <dl className="env-check">
        <dt>API base URL</dt>
        <dd>{config.isApiConfigured ? <code>{config.apiBaseUrl}</code> : <em>not configured</em>}</dd>

        <dt>API reachable</dt>
        <dd>
          {connection.status === 'loading' && 'checking…'}
          {connection.status === 'ok' && (
            <>
              yes — <strong>{connection.meta.application}</strong> {connection.meta.version}
            </>
          )}
          {connection.status === 'error' && <span role="alert">no</span>}
        </dd>
      </dl>

      {connection.status === 'error' && (
        <p className="env-error" role="alert">
          {connection.message}
        </p>
      )}
    </section>
  );
}
