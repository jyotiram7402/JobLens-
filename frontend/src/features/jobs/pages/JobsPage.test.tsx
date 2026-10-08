import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../../test/render';
import { ApiError } from '../../../services/api/ApiError';
import { jobsApi } from '../api';
import { JobsPage } from './JobsPage';
import type { JobSummary } from '../types';
import type { PageResponse } from '../../../types/api';

// The feature API is mocked rather than fetch: these tests are about what the
// page does with a result, not about how the HTTP client builds a request —
// which ApiError.test.ts already covers.
vi.mock('../api', () => ({
  jobsApi: {
    search: vi.fn(),
    byId: vi.fn(),
    byCompany: vi.fn(),
  },
}));

const searchMock = vi.mocked(jobsApi.search);

const job: JobSummary = {
  id: '22222222-2222-2222-2222-222222222222',
  title: 'Java Backend Developer',
  company: { id: '33333333-3333-3333-3333-333333333333', name: 'Example Company', slug: 'example-company' },
  location: 'Pune',
  employmentType: 'FULL_TIME',
  workMode: 'HYBRID',
  experienceMin: 1,
  experienceMax: 4,
  postedAt: new Date().toISOString(),
  active: true,
};

function page(content: JobSummary[], overrides: Partial<PageResponse<JobSummary>> = {}) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
    hasNext: false,
    hasPrevious: false,
    ...overrides,
  };
}

describe('JobsPage', () => {
  beforeEach(() => {
    searchMock.mockReset();
  });

  it('renders the search box and the filters', async () => {
    searchMock.mockResolvedValue(page([job]));

    renderWithProviders(<JobsPage />, { route: '/jobs' });

    expect(screen.getByLabelText('Search jobs')).toBeInTheDocument();
    expect(screen.getByLabelText('Location')).toBeInTheDocument();
    expect(screen.getByLabelText('Employment type')).toBeInTheDocument();
    expect(screen.getByLabelText('Working arrangement')).toBeInTheDocument();
    expect(screen.getByLabelText('Experience')).toBeInTheDocument();
    expect(screen.getByLabelText('Sort by')).toBeInTheDocument();
  });

  it('shows a loading state before results arrive', () => {
    searchMock.mockReturnValue(new Promise(() => undefined));

    renderWithProviders(<JobsPage />, { route: '/jobs' });

    expect(screen.getByText(/Loading jobs/)).toBeInTheDocument();
  });

  it('lists the jobs it receives', async () => {
    searchMock.mockResolvedValue(page([job]));

    renderWithProviders(<JobsPage />, { route: '/jobs' });

    expect(await screen.findByRole('heading', { name: 'Java Backend Developer' }))
      .toBeInTheDocument();
  });

  it('sends the filters from the URL to the backend', async () => {
    // The URL is the state, so a shared or refreshed link must reproduce the
    // same request.
    searchMock.mockResolvedValue(page([job]));

    renderWithProviders(<JobsPage />, {
      route: '/jobs?search=java&location=Pune&workMode=HYBRID&employmentType=FULL_TIME&experienceMin=3',
    });

    await waitFor(() => expect(searchMock).toHaveBeenCalled());

    expect(searchMock).toHaveBeenCalledWith(
      expect.objectContaining({
        search: 'java',
        location: 'Pune',
        workMode: 'HYBRID',
        employmentType: 'FULL_TIME',
        experienceMin: 3,
      }),
      expect.anything(),
    );
  });

  it('shows an empty state rather than an error when nothing matches', async () => {
    // An unmatched search is a successful request, and the backend returns 200
    // with an empty array for exactly that reason.
    searchMock.mockResolvedValue(page([]));

    renderWithProviders(<JobsPage />, { route: '/jobs?search=cobol' });

    expect(await screen.findByText('No jobs found')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows an error state with a retry when the request fails', async () => {
    searchMock.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'boom', {}, 'trace-1'));

    renderWithProviders(<JobsPage />, { route: '/jobs' });

    expect(await screen.findByText('Unable to load jobs.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    // The trace id is what makes a bug report answerable.
    expect(screen.getByText('trace-1')).toBeInTheDocument();
  });

  it('does not render pagination for a single page of results', async () => {
    searchMock.mockResolvedValue(page([job]));

    renderWithProviders(<JobsPage />, { route: '/jobs' });

    await screen.findByRole('heading', { name: 'Java Backend Developer' });
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument();
  });

  it('renders pagination when there is more than one page', async () => {
    searchMock.mockResolvedValue(
      page([job], { totalElements: 45, totalPages: 3, last: false, hasNext: true }),
    );

    renderWithProviders(<JobsPage />, { route: '/jobs' });

    expect(await screen.findByText(/Page 1 of 3/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();
  });
});
