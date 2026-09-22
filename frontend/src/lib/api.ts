import { config } from './config';

export interface ApiMeta {
  application: string;
  version: string;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Thrown when VITE_API_BASE_URL was never supplied to the build. */
export class ApiNotConfiguredError extends Error {
  constructor() {
    super('VITE_API_BASE_URL is not set for this build. Set it in the hosting environment (or .env.local) and redeploy.');
    this.name = 'ApiNotConfiguredError';
  }
}

/**
 * Thin fetch wrapper. Every call to the backend goes through here so that
 * auth headers, error handling and the base URL live in one place.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  if (!config.isApiConfigured) {
    throw new ApiNotConfiguredError();
  }

  let response: Response;
  try {
    response = await fetch(`${config.apiBaseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    });
  } catch {
    // fetch rejects for DNS failures, a sleeping backend, and CORS rejections.
    throw new ApiError(0, `Could not reach ${config.apiBaseUrl}. The API may be unreachable, asleep, or rejecting this origin via CORS.`);
  }

  if (!response.ok) {
    throw new ApiError(response.status, `Request to ${path} failed with HTTP ${response.status}`);
  }

  return (await response.json()) as T;
}

export function getApiMeta(): Promise<ApiMeta> {
  return apiFetch<ApiMeta>('/api/v1/meta');
}
