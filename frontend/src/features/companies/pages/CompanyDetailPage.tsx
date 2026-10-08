import { Link, useParams } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Avatar, Card, EmptyState, ErrorState, LoadingState } from '../../../components/ui';
import { useAsync } from '../../../hooks/useAsync';
import { companiesApi } from '../api';
import { jobsApi } from '../../jobs/api';
import { JobCard } from '../../jobs/components/JobCard';
import { TrackButton } from '../../tracking/components/TrackButton';

/** Enough to show the openings without turning the page into a second job search. */
const JOBS_PREVIEW_SIZE = 5;

/**
 * One company, and what they are hiring for.
 *
 * <p>The openings come from `GET /jobs?companyId=…`, not from a
 * `/companies/{id}/jobs` endpoint — the backend has no such route. Using the
 * ordinary search with one more filter means this list inherits pagination,
 * sorting and every other filter rather than being a second code path that
 * would have to grow them separately.
 *
 * <p>The two requests are independent, so a company with no jobs, or a failure
 * loading them, still shows the company.
 */
export function CompanyDetailPage() {
  const { companyId } = useParams<{ companyId: string }>();

  const company = useAsync((signal) => companiesApi.byId(companyId!, signal), [companyId]);

  const jobs = useAsync(
    (signal) => jobsApi.byCompany(companyId!, { size: JOBS_PREVIEW_SIZE }, signal),
    [companyId],
  );

  if (company.state.status === 'loading') {
    return <LoadingState message="Loading company" />;
  }

  if (company.state.status === 'error') {
    if (company.state.error.isNotFound) {
      return (
        <EmptyState
          title="Company not found"
          description="We have no record of this company."
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
        message="Unable to load this company."
        traceId={company.state.error.traceId}
        onRetry={company.reload}
      />
    );
  }

  const record = company.state.data;

  return (
    <div className="stack">
      <div className="company-header">
        <Avatar name={record.name} logoUrl={record.logoUrl} size="lg" />
        <div className="company-header-text">
          <PageHeader title={record.name} />
          <p className="detail-subtitle">
            {[record.industry, record.location].filter(Boolean).join(' · ')}
          </p>
        </div>
        {/* Fetches its own state and fails on its own: a tracking error must
            never take the company page with it. */}
        <TrackButton companyId={record.id} companyName={record.name} />
      </div>

      <Card>
        <h2 className="card-title">About</h2>
        {record.description ? (
          <p className="job-description">{record.description}</p>
        ) : (
          <p className="state-message">No description has been added for this company.</p>
        )}

        {(record.websiteUrl || record.careersUrl) && (
          <ul className="link-list">
            {record.websiteUrl && (
              <li>
                <a href={record.websiteUrl} target="_blank" rel="noopener noreferrer">
                  Website
                  <span className="sr-only"> (opens in a new tab)</span>
                </a>
              </li>
            )}
            {record.careersUrl && (
              <li>
                <a href={record.careersUrl} target="_blank" rel="noopener noreferrer">
                  Careers page
                  <span className="sr-only"> (opens in a new tab)</span>
                </a>
              </li>
            )}
          </ul>
        )}
      </Card>

      <section aria-labelledby="open-positions">
        <h2 className="section-title" id="open-positions">
          Open positions
        </h2>

        {jobs.state.status === 'loading' && <LoadingState message="Loading openings" />}

        {jobs.state.status === 'error' && (
          <ErrorState
            message="Unable to load this company's openings."
            traceId={jobs.state.error.traceId}
            onRetry={jobs.reload}
          />
        )}

        {jobs.state.status === 'success' && jobs.state.data.content.length === 0 && (
          <EmptyState
            title="No open positions"
            description={`${record.name} has no openings listed on JobLens right now.`}
          />
        )}

        {jobs.state.status === 'success' && jobs.state.data.content.length > 0 && (
          <>
            <ul className="job-list">
              {jobs.state.data.content.map((job) => (
                <li key={job.id}>
                  <JobCard job={job} />
                </li>
              ))}
            </ul>

            {jobs.state.data.hasNext && (
              <Link className="btn btn-secondary" to={`/jobs?companyId=${record.id}`}>
                View all {jobs.state.data.totalElements} openings
              </Link>
            )}
          </>
        )}
      </section>
    </div>
  );
}
