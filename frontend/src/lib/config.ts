/**
 * Runtime configuration, read from Vite environment variables.
 *
 * <p>Nothing in the codebase hard-codes a backend URL. The value differs per
 * environment and is supplied by Vercel in production.
 *
 * <p><b>VITE_API_BASE_URL is the backend origin, not the API root.</b> So
 * `http://localhost:8080`, not `http://localhost:8080/api/v1`. The version
 * prefix belongs to the API contract rather than to the deployment, so it lives
 * in code -- which means moving to `/api/v2` one day is a code change, not a
 * change every environment has to be reconfigured for. {@link apiUrl} adds it.
 *
 * <p>A missing value must not throw during module initialisation. That renders
 * a blank page with the reason buried in the console, which is the worst
 * possible failure on a freshly configured deployment. It is reported instead,
 * and the UI says so.
 *
 * <p><b>Anything prefixed `VITE_` is compiled into the bundle and readable by
 * anyone.</b> Never put a secret, password, JWT signing key or private API key
 * behind that prefix.
 */
const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

/** The versioned API prefix. Part of the contract, not of the deployment. */
const API_VERSION_PATH = '/api/v1';

export const config = {
  /** Backend origin, without a trailing slash. Empty when unconfigured. */
  apiBaseUrl: rawApiBaseUrl ? rawApiBaseUrl.replace(/\/+$/, '') : '',
  isApiConfigured: Boolean(rawApiBaseUrl),
  apiVersionPath: API_VERSION_PATH,
} as const;

/**
 * Builds an absolute URL for an API path.
 *
 * @param path a path relative to the API root, e.g. `/jobs/123`
 */
export function apiUrl(path: string): string {
  const normalised = path.startsWith('/') ? path : `/${path}`;
  return `${config.apiBaseUrl}${API_VERSION_PATH}${normalised}`;
}
