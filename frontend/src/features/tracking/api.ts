import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import type { PageParams, PageResponse } from '../../types/api';
import type { TrackedCompany, TrackingStatus } from './types';

/**
 * Company tracking. Every call needs a token, and none takes a user id — the
 * backend always acts for the user the token belongs to.
 *
 * <p>Track and untrack are idempotent on the server, so retrying either after a
 * timeout is safe: tracking twice is still one row, and untracking something
 * not tracked is still a success.
 */
export const trackingApi = {
  /** Returns the new state, so the caller needs no follow-up request. */
  track: (companyId: string) =>
    api.post<TrackingStatus>(endpoints.tracking.company(companyId)),

  /** 204 whether or not it was tracked. */
  untrack: (companyId: string) => api.delete<void>(endpoints.tracking.company(companyId)),

  status: (companyId: string, signal?: AbortSignal) =>
    api.get<TrackingStatus>(endpoints.tracking.company(companyId), { signal }),

  /** Most recently tracked first. Default page size 12, maximum 50. */
  list: (params: PageParams = {}, signal?: AbortSignal) =>
    api.get<PageResponse<TrackedCompany>>(endpoints.tracking.mine, {
      query: { ...params },
      signal,
    }),
};
