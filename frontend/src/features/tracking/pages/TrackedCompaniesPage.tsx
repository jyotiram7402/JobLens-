import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Avatar, EmptyState, ErrorState, LoadingState, Pagination } from '../../../components/ui';
import { useAsync } from '../../../hooks/useAsync';
import { formatRelativeDate } from '../../../utils/format';
import { trackingApi } from '../api';
import { TrackButton } from '../components/TrackButton';

const PAGE_SIZE = 12;

/**
 * The signed-in user's tracked companies, most recently tracked first.
 *
 * <p>Untracking from here removes the card at once, without refetching the
 * page: the server has confirmed the delete, so the list already knows the
 * answer. Refetching would only add a loading flash between the click and the
 * result.
 *
 * <p>The count shown is the server's total minus what has been untracked on
 * this page since it loaded — still a real number, just kept current without a
 * round trip.
 */
export function TrackedCompaniesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Number(searchParams.get('page') ?? '0');

  const { state, reload } = useAsync(
    (signal) => trackingApi.list({ page, size: PAGE_SIZE }, signal),
    [page],
  );

  // Company ids untracked since the last fetch.
  const [removed, setRemoved] = useState<string[]>([]);

  // Whenever a fetch completes, the server's list is the truth again. Without
  // this, ids untracked before a refetch would be subtracted from a total the
  // server has already reduced -- counting each removal twice.
  useEffect(() => {
    if (state.status === 'success') {
      setRemoved([]);
    }
  }, [state]);

  const visible =
    state.status === 'success'
      ? state.data.content.filter((company) => !removed.includes(company.companyId))
      : [];

  // Untracking the last card on a page leaves it empty while later pages may
  // still hold companies. Refetch, so they flow into this page, rather than
  // showing "nothing here" to someone who still tracks things.
  useEffect(() => {
    if (state.status === 'success' && removed.length > 0 && visible.length === 0) {
      reload();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible.length]);

  // A page with nothing on it -- its last company was untracked, or the URL
  // is stale -- steps back one rather than showing an empty page past the end.
  useEffect(() => {
    if (state.status === 'success' && state.data.content.length === 0 && page > 0) {
      setSearchParams({ page: String(page - 1) }, { replace: true });
    }
  }, [state, page, setSearchParams]);

  const total = state.status === 'success' ? state.data.totalElements - removed.length : null;

  return (
    <>
      <PageHeader
        title="Tracked companies"
        description={
          total === null
            ? 'Companies you follow.'
            : total === 1
              ? 'You track 1 company.'
              : `You track ${total} companies.`
        }
      />

      {state.status === 'loading' && <LoadingState message="Loading tracked companies" />}

      {state.status === 'error' && (
        <ErrorState
          message="Unable to load your tracked companies."
          traceId={state.error.traceId}
          onRetry={reload}
        />
      )}

      {state.status === 'success' && visible.length === 0 && removed.length === 0 && (
        <EmptyState
          title="You haven't tracked any companies yet"
          description="Explore jobs and open a company to track the ones you're interested in."
          action={
            <Link className="btn btn-primary" to="/jobs">
              Explore jobs
            </Link>
          }
        />
      )}

      {state.status === 'success' && visible.length > 0 && (
        <>
          <ul className="company-grid">
            {visible.map((company) => (
              <li key={company.companyId}>
                <article className="company-card">
                  <div className="company-card-head">
                    <Avatar name={company.name} logoUrl={company.logoUrl} />
                    <div className="company-card-headings">
                      <h2 className="company-card-name">
                        <Link to={`/companies/${company.companyId}`}>{company.name}</Link>
                      </h2>
                      {(company.industry || company.location) && (
                        <p className="company-card-meta">
                          {[company.industry, company.location].filter(Boolean).join(' · ')}
                        </p>
                      )}
                    </div>
                  </div>

                  <p className="company-card-tracked">
                    Tracked {formatRelativeDate(company.trackedAt)}
                  </p>

                  <TrackButton
                    companyId={company.companyId}
                    companyName={company.name}
                    initialTracked
                    onChange={(tracked) => {
                      if (!tracked) {
                        setRemoved((previous) => [...previous, company.companyId]);
                      }
                    }}
                  />
                </article>
              </li>
            ))}
          </ul>

          <Pagination
            page={state.data.page}
            totalPages={state.data.totalPages}
            totalElements={state.data.totalElements - removed.length}
            hasPrevious={state.data.hasPrevious}
            hasNext={state.data.hasNext}
            onChange={(nextPage) => setSearchParams({ page: String(nextPage) })}
          />
        </>
      )}
    </>
  );
}
