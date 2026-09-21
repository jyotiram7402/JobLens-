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

/**
 * Thin fetch wrapper. Every call to the backend goes through here so that
 * auth headers, error handling and the base URL live in one place.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${config.apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, `Request to ${path} failed with ${response.status}`);
  }

  return (await response.json()) as T;
}

export function getApiMeta(): Promise<ApiMeta> {
  return apiFetch<ApiMeta>('/api/v1/meta');
}
