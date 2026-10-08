import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import type { PageResponse, PageParams } from '../../types/api';
import type { JobMatch, RecommendedJob } from './types';

/**
 * Matching calls. Both require authentication, and neither takes a user id —
 * the backend always uses the profile the token points at.
 */
export const matchingApi = {
  /** The score breakdown and explanation for one job. */
  forJob: (jobId: string, signal?: AbortSignal) =>
    api.get<JobMatch>(endpoints.jobs.match(jobId), { signal }),

  /** Recent openings scored against the signed-in user's profile. */
  recommended: (params: PageParams = {}, signal?: AbortSignal) =>
    api.get<PageResponse<RecommendedJob>>(endpoints.jobs.recommended, {
      query: { ...params },
      signal,
    }),
};
