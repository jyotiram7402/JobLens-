import { useEffect, useState } from 'react';
import { getApiMeta, type ApiMeta } from '../lib/api';

type ConnectionState =
  | { status: 'loading' }
  | { status: 'ok'; meta: ApiMeta }
  | { status: 'error'; message: string };

/**
 * Landing page. For now it does one useful thing: prove the frontend is
 * correctly configured to reach the backend.
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

      <h2>API connection</h2>
      {connection.status === 'loading' && <p>Checking the backend…</p>}
      {connection.status === 'ok' && (
        <p>
          Connected to <strong>{connection.meta.application}</strong> (version{' '}
          {connection.meta.version}).
        </p>
      )}
      {connection.status === 'error' && (
        <p role="alert">Could not reach the API: {connection.message}</p>
      )}
    </section>
  );
}
