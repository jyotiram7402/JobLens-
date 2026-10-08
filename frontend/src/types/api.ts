/**
 * Shapes shared by every endpoint, mirroring the backend's `common` package.
 *
 * <p>These describe the API contract, not the backend's internals. Fields the
 * server has but does not expose -- normalized names, entity versions -- are
 * deliberately absent.
 */

/**
 * The envelope every list endpoint returns. Mirrors `PageResponse` on the
 * server, including `hasNext` / `hasPrevious`.
 *
 * <p>An empty result is a successful response with `content: []`, never a 404,
 * so UI code should render an empty state rather than treat it as an error.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

/**
 * The single error body the backend returns for every failure.
 *
 * <p>`error` is a stable machine-readable code and is what UI code should
 * branch on; `message` is human-readable and free to change. `details` maps a
 * field name to a validation message and is absent when empty.
 *
 * <p>`traceId` also comes back on the `X-Correlation-Id` response header and
 * appears on every backend log line for that request, so it is worth surfacing
 * when showing an unexpected error.
 */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  traceId?: string;
  details?: Record<string, string>;
}

/** Query parameters every paginated endpoint accepts. */
export interface PageParams {
  page?: number;
  /** Capped at 50 by the backend; a larger value is a 400. */
  size?: number;
}
