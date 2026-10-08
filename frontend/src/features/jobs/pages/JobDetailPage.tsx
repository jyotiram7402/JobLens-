import { Link, useParams } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Avatar, Card, EmptyState, ErrorState, LoadingState } from '../../../components/ui';
import { useAsync } from '../../../hooks/useAsync';
import { useAuth } from '../../auth/AuthContext';
import { MatchPanel } from '../../matching/components/MatchPanel';
import { jobsApi } from '../api';
import { EMPLOYMENT_TYPE_LABELS, WORK_MODE_LABELS } from '../types';
import { formatExperienceRange, formatRelativeDate } from '../../../utils/format';

/**
 * One job, in full.
 *
 * <p>Two independent requests: the job itself, and the match. They are
 * deliberately not combined — the job is public and almost always available,
 * the match needs a signed-in user with a filled-in profile and frequently is
 * not. Tying them together would mean a missing profile hides the job.
 */
export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const { user } = useAuth();

  const { state, reload } = useAsync(
    (signal) => jobsApi.byId(jobId!, signal),
    [jobId],
  );

  if (state.status === 'loading') {
    return <LoadingState message="Loading job details" />;
  }

  if (state.status === 'error') {
    if (state.error.isNotFound) {
      return (
        <EmptyState
          title="Job not found"
          description="This opening may have been withdrawn."
          action={
            <Link className="btn btn-primary" to="/jobs">
              Back to jobs
            </Link>
          }
        />
      );
    }
    return (
      <ErrorState
        message="Unable to load this job."
        traceId={state.error.traceId}
        onRetry={reload}
      />
    );
  }

  const job = state.data;
  const experience = formatExperienceRange(job.experienceMin, job.experienceMax);

  return (
    <div className="detail-layout">
      <div className="detail-main">
        <PageHeader title={job.title} />

        <p className="detail-subtitle">
          <Link to={`/companies/${job.company.id}`}>{job.company.name}</Link>
          {job.location && <> · {job.location}</>} · {WORK_MODE_LABELS[job.workMode]} ·{' '}
          {EMPLOYMENT_TYPE_LABELS[job.employmentType]}
        </p>

        {!job.active && (
          <p className="notice" role="status">
            This position is closed and no longer accepting applications.
          </p>
        )}

        <Card>
          <h2 className="card-title">About this role</h2>
          {job.description ? (
            /*
             * Rendered as text, inside a container with white-space: pre-wrap
             * so paragraphs survive. Never dangerouslySetInnerHTML: the
             * description comes from whoever created the job, and treating it
             * as HTML would be a script-injection route straight through the
             * most-visited page in the product.
             */
            <p className="job-description">{job.description}</p>
          ) : (
            <p className="state-message">No description was provided for this role.</p>
          )}
        </Card>

        <Card>
          <h2 className="card-title">Job details</h2>
          <dl className="detail-list">
            {job.location && (
              <>
                <dt>Location</dt>
                <dd>{job.location}</dd>
              </>
            )}
            <dt>Working arrangement</dt>
            <dd>{WORK_MODE_LABELS[job.workMode]}</dd>
            <dt>Employment type</dt>
            <dd>{EMPLOYMENT_TYPE_LABELS[job.employmentType]}</dd>
            {experience && (
              <>
                <dt>Experience</dt>
                <dd>{experience}</dd>
              </>
            )}
            <dt>Posted</dt>
            <dd>{formatRelativeDate(job.postedAt)}</dd>
          </dl>

          {job.skills.length > 0 && (
            <>
              <h3 className="match-subtitle">Skills asked for</h3>
              <ul className="tag-list">
                {job.skills.map((skill) => (
                  <li key={skill} className="tag">
                    {skill}
                  </li>
                ))}
              </ul>
            </>
          )}
        </Card>
      </div>

      <aside className="detail-aside">
        <Card>
          <h2 className="card-title">Apply</h2>
          {job.applyUrl ? (
            <>
              <p className="state-message">
                Applications are handled by {job.company.name} on their own site.
              </p>
              {/*
                rel="noopener noreferrer" is not optional on a target="_blank"
                link to a URL we did not write: without noopener the opened page
                can reach back through window.opener and navigate this one.
              */}
              <a
                className="btn btn-primary"
                href={job.applyUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                Apply for this job
                <span className="sr-only"> (opens in a new tab)</span>
              </a>
            </>
          ) : (
            <p className="state-message">
              No application link was provided. Try {job.company.name}&rsquo;s careers page.
            </p>
          )}
        </Card>

        <MatchPanel jobId={job.id} signedIn={Boolean(user)} />

        <Card>
          <h2 className="card-title">Company</h2>
          <div className="company-brief">
            <Avatar name={job.company.name} size="lg" />
            <div>
              <p className="company-brief-name">{job.company.name}</p>
              <Link to={`/companies/${job.company.id}`}>View company</Link>
            </div>
          </div>
        </Card>
      </aside>
    </div>
  );
}
