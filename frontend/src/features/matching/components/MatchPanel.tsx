import { Link } from 'react-router-dom';
import { Card, LoadingState } from '../../../components/ui';
import { useAsync } from '../../../hooks/useAsync';
import { matchingApi } from '../api';
import { MatchScore } from './MatchScore';
import { MATCH_CRITERION_LABELS } from '../types';
import type { CriterionScore, MatchCriterionKey } from '../types';

interface MatchPanelProps {
  jobId: string;
  /** False when nobody is signed in; the panel then invites them to. */
  signedIn: boolean;
}

/**
 * How this job scores for the signed-in user.
 *
 * <p>Self-contained on purpose: it fetches its own data, and a failure here
 * never takes the job page with it. Matching is the part most likely to be
 * unavailable — the user may not be signed in, or their profile may be empty —
 * and none of that is a reason to stop someone reading the job.
 *
 * <p>Nothing is ever invented. If there is no score, the panel says why and
 * offers the fix; it does not show a placeholder percentage.
 */
export function MatchPanel({ jobId, signedIn }: MatchPanelProps) {
  const { state } = useAsync(
    (signal) => (signedIn ? matchingApi.forJob(jobId, signal) : Promise.resolve(null)),
    [jobId, signedIn],
  );

  if (!signedIn) {
    return (
      <Card>
        <h2 className="card-title">Your match</h2>
        <p className="state-message">
          Sign in to see how this job scores against your profile.
        </p>
        <Link className="btn btn-primary" to="/login">
          Sign in
        </Link>
      </Card>
    );
  }

  if (state.status === 'loading') {
    return (
      <Card>
        <h2 className="card-title">Your match</h2>
        <LoadingState message="Scoring this job" />
      </Card>
    );
  }

  if (state.status === 'error') {
    // 422 means the profile has nothing to match on. That is not a failure to
    // apologise for — it is a thing the user can fix in about a minute, so the
    // panel says so and links there.
    if (state.error.code === 'PROFILE_NOT_READY') {
      return (
        <Card>
          <h2 className="card-title">Your match</h2>
          <p className="state-message">
            Complete your profile to see your personalised job match.
          </p>
          <Link className="btn btn-primary" to="/profile">
            Complete profile
          </Link>
        </Card>
      );
    }

    return (
      <Card>
        <h2 className="card-title">Your match</h2>
        <p className="state-message">
          Your match score is unavailable right now. The rest of this page still works.
        </p>
      </Card>
    );
  }

  const match = state.data;
  if (!match) {
    return null;
  }

  if (!match.scored) {
    return (
      <Card>
        <h2 className="card-title">Your match</h2>
        <p className="state-message">{match.explanation[0]}</p>
        <Link className="btn btn-primary" to="/profile">
          Complete profile
        </Link>
      </Card>
    );
  }

  const criteria = Object.entries(match.breakdown) as [MatchCriterionKey, CriterionScore][];
  const skills = match.breakdown.skills;

  return (
    <Card>
      <h2 className="card-title">Your match</h2>

      <p className="match-headline">
        <MatchScore score={match.score} size="lg" />
      </p>

      {/* A table, because this genuinely is tabular: criterion by score. The
          "not compared" row is deliberately not written as 0 — the criterion
          was excluded from the total along with its weight, which is a
          different thing and the user should not read it as a failure. */}
      <table className="match-table">
        <caption className="sr-only">Score breakdown by criterion</caption>
        <thead>
          <tr>
            <th scope="col">Criterion</th>
            <th scope="col">Score</th>
          </tr>
        </thead>
        <tbody>
          {criteria.map(([key, criterion]) => (
            <tr key={key}>
              <th scope="row">{MATCH_CRITERION_LABELS[key]}</th>
              <td>
                {criterion.applicable ? (
                  `${criterion.score}/${criterion.maxScore}`
                ) : (
                  <span className="match-not-compared">not compared</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {skills?.matchedSkills && skills.matchedSkills.length > 0 && (
        <div className="match-skills">
          <h3 className="match-subtitle">Skills you have</h3>
          <ul className="tag-list">
            {skills.matchedSkills.map((skill) => (
              // The checkmark is decorative; "Matched" is what gets announced,
              // so the meaning does not depend on seeing a symbol.
              <li key={skill} className="tag tag-positive">
                <span aria-hidden="true">✓ </span>
                <span className="sr-only">Matched: </span>
                {skill}
              </li>
            ))}
          </ul>
        </div>
      )}

      {skills?.missingSkills && skills.missingSkills.length > 0 && (
        <div className="match-skills">
          <h3 className="match-subtitle">Skills to gain</h3>
          <ul className="tag-list">
            {skills.missingSkills.map((skill) => (
              <li key={skill} className="tag tag-caution">
                <span aria-hidden="true">+ </span>
                <span className="sr-only">Missing: </span>
                {skill}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Rendered from the backend's own sentences. Nothing is generated here:
          the explanation comes from the same comparison the score did, which is
          what stops the prose and the numbers disagreeing. */}
      <div className="match-explanation">
        <h3 className="match-subtitle">Why</h3>
        <ul className="plain-list">
          {match.explanation.map((sentence) => (
            <li key={sentence}>{sentence}</li>
          ))}
        </ul>
      </div>
    </Card>
  );
}
