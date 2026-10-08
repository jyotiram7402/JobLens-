import { Link } from 'react-router-dom';
import { Card, EmptyState, ErrorState, LoadingState } from '../../../components/ui';
import { useAsync } from '../../../hooks/useAsync';
import { useAuth } from '../../auth/AuthContext';
import { matchingApi } from '../../matching/api';
import { jobsApi } from '../../jobs/api';
import { JobCard } from '../../jobs/components/JobCard';
import { greeting } from '../../../utils/format';
import type { UserProfile } from '../../profile/types';

const RECOMMENDED_COUNT = 5;
const RECENT_COUNT = 4;

/** A score at or above this is called a strong match in the UI. */
const STRONG_MATCH = 75;

/**
 * The six things matching actually uses.
 *
 * <p>Profile completion is measured against these rather than against every
 * field on the profile, because these are the ones that change a score. A
 * "90% complete" figure driven by a summary paragraph nobody matches on would
 * be a number that means nothing.
 */
const PROFILE_FIELDS: { label: string; isSet: (profile: UserProfile) => boolean }[] = [
  { label: 'Skills', isSet: (p) => p.skills.length > 0 },
  { label: 'Years of experience', isSet: (p) => p.yearsOfExperience !== null },
  { label: 'Preferred roles', isSet: (p) => p.preferredRoles.length > 0 },
  { label: 'Preferred locations', isSet: (p) => p.preferredLocations.length > 0 },
  { label: 'Working arrangement', isSet: (p) => p.remotePreference !== 'ANY' },
  { label: 'Headline', isSet: (p) => Boolean(p.headline) },
];

/**
 * The authenticated landing page.
 *
 * <p>Deliberately not a wall of statistics. Every number here is either
 * returned by the backend or derived from data already on the page — nothing is
 * invented to make the dashboard look busy, which would make every other number
 * on it untrustworthy too.
 *
 * <p>Tracked companies are genuinely absent (step 10), so the tile says so
 * rather than showing a zero that looks like a fact.
 */
export function DashboardPage() {
  const { user } = useAuth();

  const recommended = useAsync(
    (signal) => matchingApi.recommended({ size: RECOMMENDED_COUNT }, signal),
    [],
  );

  const recent = useAsync(
    (signal) => jobsApi.search({ size: RECENT_COUNT, sort: 'postedAt,desc' }, signal),
    [],
  );

  if (!user) {
    return <LoadingState message="Loading your dashboard" />;
  }

  const completedFields = PROFILE_FIELDS.filter((field) => field.isSet(user.profile));
  const missingFields = PROFILE_FIELDS.filter((field) => !field.isSet(user.profile));
  const completion = Math.round((completedFields.length / PROFILE_FIELDS.length) * 100);

  const recommendedTotal =
    recommended.state.status === 'success' ? recommended.state.data.totalElements : null;

  // Counted over the recommendations actually loaded, and labelled as such.
  // Claiming a total across every job would be a number we have not computed.
  const strongMatches =
    recommended.state.status === 'success'
      ? recommended.state.data.content.filter((item) => item.scored && item.score >= STRONG_MATCH)
          .length
      : null;

  return (
    <div className="stack">
      <section className="dashboard-greeting">
        <h1 className="page-title">
          {greeting()}, {user.firstName}
        </h1>
        <p className="page-description">Discover companies. Discover opportunities.</p>
      </section>

      <section className="stat-grid" aria-label="Your overview">
        <Card className="stat">
          <p className="stat-label">Recommended jobs</p>
          <p className="stat-value">{recommendedTotal ?? '—'}</p>
          <p className="stat-note">Scored against your profile</p>
        </Card>

        <Card className="stat">
          <p className="stat-label">Strong matches</p>
          <p className="stat-value">{strongMatches ?? '—'}</p>
          <p className="stat-note">{STRONG_MATCH}% or above, in your top {RECOMMENDED_COUNT}</p>
        </Card>

        <Card className="stat">
          <p className="stat-label">Profile completion</p>
          <p className="stat-value">{completion}%</p>
          <p className="stat-note">
            {missingFields.length === 0
              ? 'Everything matching uses is filled in'
              : `Missing: ${missingFields.map((field) => field.label).join(', ')}`}
          </p>
        </Card>

        <Card className="stat">
          <p className="stat-label">Tracked companies</p>
          <p className="stat-value stat-value-pending">Coming soon</p>
          <p className="stat-note">Following companies arrives in a later release</p>
        </Card>
      </section>

      {completion < 100 && (
        <Card>
          <h2 className="card-title">Improve your matches</h2>
          <p className="state-message">
            JobLens only scores what you have told it about. Adding{' '}
            {missingFields.map((field) => field.label.toLowerCase()).join(', ')} will make your
            match scores more complete.
          </p>
          <Link className="btn btn-primary" to="/profile">
            Update profile
          </Link>
        </Card>
      )}

      <section aria-labelledby="recommended-heading">
        <div className="section-head">
          <h2 className="section-title" id="recommended-heading">
            Recommended for you
          </h2>
        </div>

        {recommended.state.status === 'loading' && (
          <LoadingState message="Finding jobs for you" />
        )}

        {recommended.state.status === 'error' &&
          (recommended.state.error.code === 'PROFILE_NOT_READY' ? (
            <EmptyState
              title="No recommendations yet"
              description="Add your skills and experience so JobLens can match jobs to you."
              action={
                <Link className="btn btn-primary" to="/profile">
                  Complete profile
                </Link>
              }
            />
          ) : (
            <ErrorState
              message="Unable to load your recommendations."
              traceId={recommended.state.error.traceId}
              onRetry={recommended.reload}
            />
          ))}

        {recommended.state.status === 'success' &&
          recommended.state.data.content.length === 0 && (
            <EmptyState
              title="No recommendations available yet"
              description="There are no openings to score against your profile right now."
              action={
                <Link className="btn btn-secondary" to="/jobs">
                  Browse all jobs
                </Link>
              }
            />
          )}

        {recommended.state.status === 'success' && recommended.state.data.content.length > 0 && (
          <ul className="job-list">
            {recommended.state.data.content.map((item) => (
              <li key={item.job.id}>
                <JobCard
                  job={item.job}
                  matchScore={item.score}
                  matchScored={item.scored}
                />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="recent-heading">
        <div className="section-head">
          <h2 className="section-title" id="recent-heading">
            Recently posted
          </h2>
          <Link to="/jobs">View all jobs</Link>
        </div>

        {recent.state.status === 'loading' && <LoadingState message="Loading recent jobs" />}

        {recent.state.status === 'error' && (
          <ErrorState
            message="Unable to load recent jobs."
            traceId={recent.state.error.traceId}
            onRetry={recent.reload}
          />
        )}

        {recent.state.status === 'success' && recent.state.data.content.length === 0 && (
          <EmptyState
            title="No jobs yet"
            description="There are no openings listed on JobLens right now."
          />
        )}

        {recent.state.status === 'success' && recent.state.data.content.length > 0 && (
          <ul className="job-list">
            {recent.state.data.content.map((job) => (
              <li key={job.id}>
                <JobCard job={job} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
