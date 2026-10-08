import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import type { PageResponse } from '../../types/api';
import type { Job, JobSearchParams, JobSummary } from './types';

/**
 * Job calls.
 *
 * <p>Note what {@link searchJobs} does <em>not</em> do: it never fetches
 * everything and filters in the browser. Every filter is a query parameter the
 * backend applies in SQL, which is the only version of this that works once
 * there are more jobs than fit on a page.
 */
export const jobsApi = {
  /**
   * Searches jobs. Unset parameters are dropped by the client, so an empty
   * filter is genuinely absent rather than sent as an empty string — which the
   * backend would treat as a filter on "".
   */
  search: (params: JobSearchParams, signal?: AbortSignal) =>
    api.get<PageResponse<JobSummary>>(endpoints.jobs.search, {
      query: { ...params },
      signal,
    }),

  byId: (jobId: string, signal?: AbortSignal) =>
    api.get<Job>(endpoints.jobs.byId(jobId), { signal }),

  /**
   * A company's openings.
   *
   * <p>There is no `/companies/{id}/jobs` endpoint on the backend. This is the
   * ordinary job search with one more filter, which means the company page gets
   * pagination, sorting and every other filter for free instead of a second
   * code path that would have to grow them separately.
   */
  byCompany: (companyId: string, params: JobSearchParams = {}, signal?: AbortSignal) =>
    api.get<PageResponse<JobSummary>>(endpoints.jobs.search, {
      query: { ...params, companyId },
      signal,
    }),
};
