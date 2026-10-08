import type { ApiErrorBody } from '../../types/api';

/**
 * Any failed API call, in one type.
 *
 * <p>UI code catches this rather than inspecting responses, so error handling
 * looks the same whether the server returned a structured 400, an unexpected
 * 500, or the request never arrived at all.
 *
 * <p>A raw server message is never shown for a 5xx. The backend already keeps
 * internals out of those responses, and the frontend does not undo that by
 * rendering whatever arrives.
 */
export class ApiError extends Error {
  /** HTTP status, or 0 when the request never reached the server. */
  readonly status: number;

  /** Stable machine-readable code, e.g. `COMPANY_NOT_FOUND`. */
  readonly code: string;

  /** Field name to validation message, for a 400. */
  readonly details: Record<string, string>;

  /** Correlation id, worth quoting in a bug report. */
  readonly traceId?: string;

  constructor(status: number, code: string, message: string,
              details: Record<string, string> = {}, traceId?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.traceId = traceId;
  }

  /** The request never reached the server: offline, DNS, CORS, or asleep. */
  static network(): ApiError {
    return new ApiError(0, 'NETWORK_ERROR',
      'Could not reach JobLens. Check your connection and try again.');
  }

  /** `VITE_API_BASE_URL` was never supplied to the build. */
  static notConfigured(): ApiError {
    return new ApiError(0, 'API_NOT_CONFIGURED',
      'This deployment has no API address configured. Set VITE_API_BASE_URL and redeploy.');
  }

  /**
   * Builds an error from a failed response, falling back when the body is not
   * the shape we expect -- a proxy returning HTML, for instance.
   */
  static async fromResponse(response: Response): Promise<ApiError> {
    let body: Partial<ApiErrorBody> | undefined;
    try {
      body = (await response.json()) as Partial<ApiErrorBody>;
    } catch {
      body = undefined;
    }

    return new ApiError(
      response.status,
      body?.error ?? 'UNEXPECTED_ERROR',
      body?.message ?? 'Something went wrong. Please try again.',
      body?.details ?? {},
      body?.traceId,
    );
  }

  /** True when the user needs to sign in (or sign in again). */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  /** True when a field-level message is available for a form. */
  get hasFieldErrors(): boolean {
    return Object.keys(this.details).length > 0;
  }
}
