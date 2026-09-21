/**
 * Runtime configuration read from Vite environment variables.
 * Nothing in the codebase should hard-code a backend URL.
 */
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

if (!apiBaseUrl) {
  throw new Error('VITE_API_BASE_URL is not set. Copy .env.example to .env.local.');
}

export const config = {
  apiBaseUrl: apiBaseUrl.replace(/\/$/, ''),
} as const;
