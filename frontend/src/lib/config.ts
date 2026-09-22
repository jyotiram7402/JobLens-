/**
 * Runtime configuration read from Vite environment variables.
 * Nothing in the codebase should hard-code a backend URL.
 *
 * A missing VITE_API_BASE_URL must not throw during module initialisation --
 * that renders a blank white page with the reason buried in the console, which
 * is the worst possible failure mode on a freshly deployed environment.
 * Instead the value is reported as missing and surfaced in the UI.
 */
const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

export const config = {
  /** Empty string when unconfigured. Callers check `isApiConfigured` first. */
  apiBaseUrl: rawApiBaseUrl ? rawApiBaseUrl.replace(/\/+$/, '') : '',
  isApiConfigured: Boolean(rawApiBaseUrl),
} as const;
