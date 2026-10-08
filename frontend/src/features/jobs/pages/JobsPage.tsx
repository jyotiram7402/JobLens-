import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../components/layout/PageHeader';
import { EmptyState, ErrorState, LoadingState, Pagination } from '../../../components/ui';
import { useAsync } from '../../../hooks/useAsync';
import { useDebouncedValue } from '../../../hooks/useDebouncedValue';
import { jobsApi } from '../api';
import { JobCard } from '../components/JobCard';
import { JobFilters } from '../components/JobFilters';
import type { FilterValues } from '../components/JobFilters';
import type { EmploymentType, JobSearchParams, WorkMode } from '../types';

const PAGE_SIZE = 20;
const DEFAULT_SORT = 'postedAt,desc';

/**
 * Job discovery.
 *
 * <p><b>The URL is the state.</b> Every filter lives in the query string, which
 * is what makes a search refreshable, shareable, and navigable with the browser
 * Back button. Holding the same values in component state as well would mean
 * two sources of truth that drift the moment someone edits the address bar.
 *
 * <p>The one exception is the keyword box, which keeps a local value so typing
 * stays responsive, and writes it to the URL once the user pauses. Pushing
 * every keystroke into the URL would fill the history with half-typed words and
 * fire a request for each one.
 *
 * <p>All filtering happens on the backend. Nothing here fetches a large list
 * and narrows it in the browser — that only works until there is more data than
 * fits in a page, which is to say it never really works.
 */
export function JobsPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const search = searchParams.get('search') ?? '';
  const page = Number(searchParams.get('page') ?? '0');

  const filters: FilterValues = {
    location: searchParams.get('location') ?? '',
    employmentType: searchParams.get('employmentType') ?? '',
    workMode: searchParams.get('workMode') ?? '',
    experience: searchParams.get('experienceMin') ?? '',
    sort: searchParams.get('sort') ?? DEFAULT_SORT,
  };

  const [keyword, setKeyword] = useState(search);
  const debouncedKeyword = useDebouncedValue(keyword);

  /**
   * Writes a set of changes into the URL.
   *
   * <p>Any change other than paging resets to page 0: staying on page 4 while
   * narrowing a search is how a user ends up looking at an empty page and
   * concluding there are no results.
   */
  function updateParams(changes: Record<string, string>, options?: { keepPage?: boolean }) {
    const next = new URLSearchParams(searchParams);

    for (const [key, value] of Object.entries(changes)) {
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
    }
    if (!options?.keepPage) {
      next.delete('page');
    }

    // replace, so a filter adjustment does not add a history entry per
    // keystroke-pause — Back should leave the search, not step through it.
    setSearchParams(next, { replace: true });
  }

  // Pushes the debounced keyword into the URL once typing settles.
  useEffect(() => {
    if (debouncedKeyword !== search) {
      updateParams({ search: debouncedKeyword });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  // Keeps the box in step when the URL changes from somewhere else: Back,
  // Clear filters, or a shared link.
  useEffect(() => {
    setKeyword(search);
  }, [search]);

  const params: JobSearchParams = {
    search: search || undefined,
    location: filters.location || undefined,
    employmentType: (filters.employmentType as EmploymentType) || undefined,
    workMode: (filters.workMode as WorkMode) || undefined,
    experienceMin: filters.experience ? Number(filters.experience) : undefined,
    sort: filters.sort || undefined,
    page,
    size: PAGE_SIZE,
  };

  const { state, reload } = useAsync(
    (signal) => jobsApi.search(params, signal),
    // The query string is the complete description of this request, so it is
    // exactly the right dependency — and it is a primitive, so no deep compare.
    [searchParams.toString()],
  );

  const filtersActive =
    Boolean(search) ||
    Boolean(filters.location) ||
    Boolean(filters.employmentType) ||
    Boolean(filters.workMode) ||
    Boolean(filters.experience) ||
    filters.sort !== DEFAULT_SORT;

  return (
    <>
      <PageHeader title="Jobs" description="Search openings by keyword, location and more." />

      <form className="search-bar" role="search" onSubmit={(event) => event.preventDefault()}>
        <label className="field-label" htmlFor="job-search">
          Search jobs
        </label>
        <input
          id="job-search"
          className="field-input search-input"
          type="search"
          placeholder="Java, Spring Boot, React…"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
      </form>

      <JobFilters
        values={filters}
        active={filtersActive}
        onChange={(field, value) =>
          updateParams({
            [field === 'experience' ? 'experienceMin' : field]: value,
          })
        }
        onClear={() => setSearchParams(new URLSearchParams(), { replace: true })}
      />

      {/* aria-live so a screen reader is told the results changed; the list
          updating silently is otherwise invisible to anyone not watching it. */}
      <section aria-label="Search results" aria-live="polite" aria-busy={state.status === 'loading'}>
        {state.status === 'loading' && <LoadingState message="Loading jobs" />}

        {state.status === 'error' && (
          <ErrorState
            message="Unable to load jobs."
            traceId={state.error.traceId}
            onRetry={reload}
          />
        )}

        {state.status === 'success' && state.data.content.length === 0 && (
          <EmptyState
            title="No jobs found"
            description={
              filtersActive
                ? 'Try a different search term, or clear some filters.'
                : 'There are no openings listed yet.'
            }
          />
        )}

        {state.status === 'success' && state.data.content.length > 0 && (
          <>
            <ul className="job-list">
              {state.data.content.map((job) => (
                <li key={job.id}>
                  <JobCard job={job} />
                </li>
              ))}
            </ul>

            <Pagination
              page={state.data.page}
              totalPages={state.data.totalPages}
              totalElements={state.data.totalElements}
              hasPrevious={state.data.hasPrevious}
              hasNext={state.data.hasNext}
              onChange={(nextPage) => {
                updateParams({ page: String(nextPage) }, { keepPage: true });
                window.scrollTo({ top: 0 });
              }}
            />
          </>
        )}
      </section>
    </>
  );
}
