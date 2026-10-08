import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, testUser } from '../../../test/render';
import { ApiError } from '../../../services/api/ApiError';
import { matchingApi } from '../../matching/api';
import { jobsApi } from '../../jobs/api';
import { DashboardPage } from './DashboardPage';
import type { JobSummary } from '../../jobs/types';

vi.mock('../../matching/api', () => ({
  matchingApi: { forJob: vi.fn(), recommended: vi.fn() },
}));

vi.mock('../../jobs/api', () => ({
  jobsApi: { search: vi.fn(), byId: vi.fn(), byCompany: vi.fn() },
}));

const recommendedMock = vi.mocked(matchingApi.recommended);
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

function emptyPage<T>() {
  return {
    content: [] as T[],
    page: 0, size: 20, totalElements: 0, totalPages: 0,
    first: true, last: true, hasNext: false, hasPrevious: false,
  };
}

describe('DashboardPage', () => {
  beforeEach(() => {
    recommendedMock.mockReset();
    searchMock.mockReset();
    recommendedMock.mockResolvedValue(emptyPage());
    searchMock.mockResolvedValue(emptyPage());
  });

  it('greets the signed-in user by their real name', async () => {
    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Test');
  });

  it('shows recommended jobs with their match scores', async () => {
    recommendedMock.mockResolvedValue({
      ...emptyPage(),
      content: [{ job, scored: true, score: 88, explanation: ['Scores 88.'] }],
      totalElements: 12,
      totalPages: 1,
    });

    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByRole('heading', { name: 'Java Backend Developer' }))
      .toBeInTheDocument();
    expect(screen.getByText('88%')).toBeInTheDocument();
  });

  it('shows recent jobs and a link to all of them', async () => {
    searchMock.mockResolvedValue({ ...emptyPage(), content: [job], totalElements: 1, totalPages: 1 });

    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByRole('heading', { name: 'Recently posted' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View all jobs' })).toHaveAttribute('href', '/jobs');
  });

  it('derives profile completion from the fields matching actually uses', async () => {
    // The test user has five of the six, so this is a real calculation rather
    // than a number chosen to look good.
    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByText('83%')).toBeInTheDocument();
    expect(screen.getByText(/Missing: Summary|Missing: /)).toBeInTheDocument();
  });

  it('says tracked companies are not built yet instead of showing a zero', async () => {
    // A zero would read as a fact about the user's data. It is not: the
    // feature does not exist.
    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByText('Coming soon')).toBeInTheDocument();
  });

  it('asks for a profile instead of reporting an error when there is nothing to match', async () => {
    recommendedMock.mockRejectedValue(
      new ApiError(422, 'PROFILE_NOT_READY', 'Add some skills…'),
    );

    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByText('No recommendations yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Complete profile' })).toBeInTheDocument();
  });

  it('shows an empty state when there are simply no recommendations', async () => {
    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByText('No recommendations available yet')).toBeInTheDocument();
  });

  it('reports a failure to load recommendations without breaking the page', async () => {
    recommendedMock.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'boom'));
    searchMock.mockResolvedValue({ ...emptyPage(), content: [job], totalElements: 1, totalPages: 1 });

    renderWithProviders(<DashboardPage />, { user: testUser });

    expect(await screen.findByText('Unable to load your recommendations.')).toBeInTheDocument();
    // The rest of the dashboard still works.
    expect(screen.getByRole('heading', { name: 'Java Backend Developer' })).toBeInTheDocument();
  });
});
