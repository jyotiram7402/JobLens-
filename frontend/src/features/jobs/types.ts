/**
 * Jobs. Mirrors `JobResponse`, `JobSummary` and `JobCompanyRef`.
 */

export type EmploymentType =
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'CONTRACT'
  | 'INTERNSHIP'
  | 'TEMPORARY'
  | 'OTHER';

export type WorkMode = 'ONSITE' | 'HYBRID' | 'REMOTE';

/** The company as it appears nested in a job: enough to render a row and link. */
export interface JobCompanyRef {
  id: string;
  name: string;
  slug: string;
}

/**
 * The full record, from `/jobs/{id}`.
 *
 * <p>Note `applyUrl` — the backend has no `applicationUrl` and no `source`
 * field; aggregating external job sources is a V2 concern.
 *
 * <p>`experienceMin` / `experienceMax` are null when the employer did not say,
 * which matching treats as an open bound rather than as zero.
 */
export interface Job {
  id: string;
  title: string;
  company: JobCompanyRef;
  description: string | null;
  location: string | null;
  employmentType: EmploymentType;
  workMode: WorkMode;
  experienceMin: number | null;
  experienceMax: number | null;
  applyUrl: string | null;
  /** Display names of the skills this opening asks for. */
  skills: string[];
  /** When the employer published it, not when JobLens recorded it. */
  postedAt: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/** A search result row: no description, no skills, no audit timestamps. */
export interface JobSummary {
  id: string;
  title: string;
  company: JobCompanyRef;
  location: string | null;
  employmentType: EmploymentType;
  workMode: WorkMode;
  experienceMin: number | null;
  experienceMax: number | null;
  postedAt: string;
  active: boolean;
}

/**
 * Every filter `/jobs` accepts. All optional; omitted means "do not filter".
 *
 * <p>Filters combine with AND, and `search` matches the title OR the
 * description.
 */
export interface JobSearchParams {
  search?: string;
  companyId?: string;
  location?: string;
  employmentType?: EmploymentType;
  workMode?: WorkMode;
  /** 0-60. Matched as a range overlap against the job's. */
  experienceMin?: number;
  experienceMax?: number;
  /** Defaults to true. Anything else requires authentication. */
  active?: boolean;
  /** ISO-8601 instant, inclusive. */
  postedAfter?: string;
  postedBefore?: string;
  page?: number;
  /** Capped at 50. */
  size?: number;
  /** `postedAt`, `createdAt` or `title`, optionally `,asc` / `,desc`. */
  sort?: string;
}

/** Human-readable labels. The API returns enum names; users should not see them. */
export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: 'Full time',
  PART_TIME: 'Part time',
  CONTRACT: 'Contract',
  INTERNSHIP: 'Internship',
  TEMPORARY: 'Temporary',
  OTHER: 'Other',
};

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  ONSITE: 'On site',
  HYBRID: 'Hybrid',
  REMOTE: 'Remote',
};
