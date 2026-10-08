/**
 * Every backend path the frontend knows about, in one place.
 *
 * <p>Paths are relative to the API root; `apiUrl` adds the origin and the
 * `/api/v1` prefix. Keeping them here rather than scattered as string literals
 * means a renamed route is one edit, and it doubles as the documented contract
 * between the two halves of the project.
 *
 * <p>Taken from the backend source, not assumed. Two things worth knowing:
 *
 * <ul>
 *   <li>There is <b>no</b> {@code /companies/{id}/jobs} endpoint. Jobs for a
 *       company come from {@code /jobs?companyId=…}, which is the same search
 *       endpoint with one more filter — one code path, one set of behaviours.</li>
 *   <li>The job field is {@code applyUrl}. There is no {@code source} field;
 *       job aggregation is a V2 concern.</li>
 * </ul>
 */
export const endpoints = {
  /** Public. Application name, version and environment — a cheap liveness probe. */
  meta: '/meta',

  auth: {
    /** Public. Returns the created user and **no token**; call login next. */
    register: '/auth/register',
    /** Public. Returns `{ accessToken, tokenType, expiresIn, user }`. */
    login: '/auth/login',
  },

  users: {
    /** Authenticated. The account plus its career profile. */
    me: '/users/me',
    /** Authenticated. GET to read, PUT to replace. */
    myProfile: '/users/me/profile',
  },

  companies: {
    /** Public. Paginated search: `search`, `page`, `size`. */
    search: '/companies',
    /** Public. */
    byId: (companyId: string) => `/companies/${companyId}`,
    /** Public. The readable URL identifier, for shareable links. */
    bySlug: (slug: string) => `/companies/by-slug/${slug}`,
    /** Authenticated. */
    create: '/companies',
    /** Authenticated. */
    update: (companyId: string) => `/companies/${companyId}`,
  },

  jobs: {
    /**
     * Public. Search and filtering — `search`, `companyId`, `location`,
     * `employmentType`, `workMode`, `experienceMin`, `experienceMax`,
     * `postedAfter`, `postedBefore`, `page`, `size`, `sort`.
     *
     * `active` defaults to true; asking for anything else needs a token.
     */
    search: '/jobs',
    /** Public. */
    byId: (jobId: string) => `/jobs/${jobId}`,
    /** Authenticated. Recent openings scored against your profile. */
    recommended: '/jobs/recommended',
    /** Authenticated. The score breakdown and explanation for one job. */
    match: (jobId: string) => `/jobs/${jobId}/match`,
    /** Authenticated. */
    create: '/jobs',
    /** Authenticated. */
    update: (jobId: string) => `/jobs/${jobId}`,
    /** Authenticated. Withdraws from search; there is no DELETE. */
    close: (jobId: string) => `/jobs/${jobId}/close`,
  },
} as const;
