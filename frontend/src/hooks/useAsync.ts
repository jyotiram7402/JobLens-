import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../services/api/ApiError';

export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: ApiError };

/**
 * Runs an async operation and tracks loading, success and failure.
 *
 * <p>Every data-backed page needs the same three states, and writing them by
 * hand in each one is how pages end up handling errors differently — or not at
 * all. This is deliberately small: no cache, no retries, no deduplication.
 *
 * <p>If several pages later need the same data at the same time, that is the
 * signal to reach for TanStack Query, which can wrap this without any page
 * changing. Adding it now would be a dependency ahead of the problem.
 *
 * <p>Two correctness details that are easy to get wrong by hand:
 *
 * <ul>
 *   <li>The request is aborted when the effect is cleaned up, so navigating
 *       away mid-flight does not set state on an unmounted component.</li>
 *   <li>A stale response is ignored. Without that, typing a search quickly can
 *       let an earlier, slower response overwrite a later one — the list then
 *       shows results for a query the user has already changed.</li>
 * </ul>
 *
 * @param operation receives an AbortSignal; pass it to the API client
 * @param deps      re-runs when these change, like useEffect
 */
export function useAsync<T>(
  operation: (signal: AbortSignal) => Promise<T>,
  deps: unknown[],
): { state: AsyncState<T>; reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  const reload = useCallback(() => setAttempt((previous) => previous + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let current = true;

    setState({ status: 'loading' });

    operation(controller.signal)
      .then((data) => {
        if (current) {
          setState({ status: 'success', data });
        }
      })
      .catch((error: unknown) => {
        // An abort is this hook's own doing, not something to report.
        if (!current || (error instanceof DOMException && error.name === 'AbortError')) {
          return;
        }
        setState({
          status: 'error',
          error: error instanceof ApiError ? error : ApiError.network(),
        });
      });

    return () => {
      current = false;
      controller.abort();
    };
    // operation is intentionally not a dependency: callers pass an inline
    // arrow function, which is a new reference on every render and would make
    // this loop forever. The deps array is the caller's explicit statement of
    // what the operation actually depends on.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);

  return { state, reload };
}
