import { apiUrl, config } from '../../lib/config';
import { tokenStorage } from '../auth/tokenStorage';
import { ApiError } from './ApiError';

/**
 * The one place the application talks to the backend.
 *
 * <p>Every request goes through here, which is what lets the base URL, the
 * `Authorization` header, JSON encoding and error translation be decided once
 * rather than at each call site. A feature that fetches directly loses all four.
 *
 * <p>No data-fetching library. TanStack Query is excellent and brings caching,
 * retries and request deduplication — none of which this application needs yet,
 * because it has no pages that fetch the same thing twice. Adding it now would
 * be a dependency and a set of conventions adopted ahead of the problem they
 * solve. The moment a real caching need appears it can wrap these functions
 * without any feature code changing.
 */

type QueryValue = string | number | boolean | undefined | null;

export interface RequestOptions {
  /** Appended as a query string; `undefined` and `null` entries are dropped. */
  query?: Record<string, QueryValue>;
  /** Lets a caller cancel, e.g. when a component unmounts mid-request. */
  signal?: AbortSignal;
}

/**
 * Builds a query string, leaving out anything unset.
 *
 * <p>That matters for job search, where "no filter" and "filter on nothing" are
 * different requests: sending `?location=` would filter on an empty string.
 */
function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) {
    return '';
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : '';
}

async function request<T>(method: string, path: string, body?: unknown,
                          options?: RequestOptions): Promise<T> {
  if (!config.isApiConfigured) {
    throw ApiError.notConfigured();
  }

  const headers: Record<string, string> = { Accept: 'application/json' };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  // Attached per request rather than captured once, so a token acquired after
  // the module loaded is picked up without a reload.
  const token = tokenStorage.get();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${apiUrl(path)}${buildQuery(options?.query)}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: options?.signal,
    });
  } catch (cause) {
    // An aborted request is the caller's own doing, not a failure to report.
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    // fetch rejects for offline, DNS failures, a sleeping free-tier backend and
    // CORS rejections alike — the browser deliberately does not say which.
    throw ApiError.network();
  }

  if (!response.ok) {
    throw await ApiError.fromResponse(response);
  }

  // 204, and any other empty body: there is nothing to parse, and calling
  // json() would throw.
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>('GET', path, undefined, options),

  post: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('POST', path, body, options),

  put: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('PUT', path, body, options),

  delete: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>('DELETE', path, undefined, options),
};
