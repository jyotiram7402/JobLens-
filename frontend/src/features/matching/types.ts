/**
 * Job matching. Mirrors `MatchResponse`, `CriterionScore` and `RecommendedJob`.
 */
import type { JobSummary } from '../jobs/types';

/** The five criteria, as they are keyed in the `breakdown` object. */
export type MatchCriterionKey =
  | 'skills'
  | 'experience'
  | 'location'
  | 'role'
  | 'workMode';

/**
 * One criterion's contribution.
 *
 * <p><b>`applicable: false` is not a zero.</b> It means there was not enough
 * data on one side or the other to judge, so the criterion was excluded from
 * the total along with its weight — `maxScore` is then 0, which is what makes
 * the breakdown add up. UI should show the `reason` rather than a 0/50 bar,
 * because the user has not failed anything.
 *
 * <p>`matchedSkills` and `missingSkills` are present only on the `skills`
 * criterion.
 */
export interface CriterionScore {
  score: number;
  /** 0 when the criterion was not applicable. */
  maxScore: number;
  applicable: boolean;
  /** True when the criterion scored at or above half its weight. */
  matched: boolean;
  /** A short factual sentence, safe to show as-is. */
  reason: string;
  matchedSkills?: string[];
  missingSkills?: string[];
}

/**
 * The result of scoring one job against the signed-in user's profile.
 *
 * <p>`scored: false` means nothing could be compared at all. That is different
 * from a score of 0 — "we cannot tell" rather than "terrible match" — and the
 * UI must not render a 0% ring for it.
 *
 * <p>`explanation` is generated server-side from the same comparison the score
 * came from, so it can be rendered directly. It is a list of sentences in
 * reading order: the score first, then strengths, then gaps.
 */
export interface JobMatch {
  jobId: string;
  scored: boolean;
  /** 0-100. Meaningless when `scored` is false. */
  score: number;
  /** Always 100. Per-criterion maxima sum to the achievable total, not this. */
  maxScore: number;
  breakdown: Record<MatchCriterionKey, CriterionScore>;
  explanation: string[];
}

/** A row from `/jobs/recommended`, carrying a trimmed explanation. */
export interface RecommendedJob {
  job: JobSummary;
  scored: boolean;
  score: number;
  /** The first couple of sentences; fetch the full match for the rest. */
  explanation: string[];
}

/** Display labels for the breakdown. */
export const MATCH_CRITERION_LABELS: Record<MatchCriterionKey, string> = {
  skills: 'Skills',
  experience: 'Experience',
  location: 'Location',
  role: 'Role',
  workMode: 'Working arrangement',
};
