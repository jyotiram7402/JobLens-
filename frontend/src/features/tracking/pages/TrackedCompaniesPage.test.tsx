import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderWithProviders, testUser } from '../../../test/render';
import { ApiError } from '../../../services/api/ApiError';
import { trackingApi } from '../api';
import { TrackedCompaniesPage } from './TrackedCompaniesPage';
import type { TrackedCompany } from '../types';
import type { PageResponse } from '../../../types/api';

vi.mock('../api', () => ({
  trackingApi: {
    track: vi.fn(),
    untrack: vi.fn(),
    status: vi.fn(),
    list: vi.fn(),
  },
}));

const listMock = vi.mocked(trackingApi.list);
const untrackMock = vi.mocked(trackingApi.untrack);

function company(id: string, name: string): TrackedCompany {
  return {
    companyId: id,
    name,
    slug: name.toLowerCase().replace(/\s+/g, '-'),
    logoUrl: null,
    industry: 'Information Technology',
    location: 'Pune, India',
    websiteUrl: null,
    careersUrl: null,
    trackedAt: new Date().toISOString(),
  };
}

function page(content: TrackedCompany[]): PageResponse<TrackedCompany> {
  return {
    content,
    page: 0,
    size: 12,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
    hasNext: false,
    hasPrevious: false,
  };
}

const alpha = company('11111111-1111-1111-1111-111111111111', 'Alpha Systems');
const beta = company('22222222-2222-2222-2222-222222222222', 'Beta Industries');

describe('TrackedCompaniesPage', () => {
  beforeEach(() => {
    listMock.mockReset();
    untrackMock.mockReset();
  });

  it('lists the tracked companies with links to them', async () => {
    listMock.mockResolvedValue(page([alpha, beta]));

    renderWithProviders(<TrackedCompaniesPage />, { user: testUser });

    expect(await screen.findByRole('link', { name: 'Alpha Systems' })).toHaveAttribute(
      'href',
      `/companies/${alpha.companyId}`,
    );
    expect(screen.getByRole('link', { name: 'Beta Industries' })).toBeInTheDocument();
    expect(screen.getByText('You track 2 companies.')).toBeInTheDocument();
  });

  it('shows a loading state first', () => {
    listMock.mockReturnValue(new Promise(() => undefined));

    renderWithProviders(<TrackedCompaniesPage />, { user: testUser });

    expect(screen.getByText(/Loading tracked companies/)).toBeInTheDocument();
  });

  it('shows a helpful empty state rather than an empty grid', async () => {
    listMock.mockResolvedValue(page([]));

    renderWithProviders(<TrackedCompaniesPage />, { user: testUser });

    expect(await screen.findByText("You haven't tracked any companies yet")).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Explore jobs' })).toHaveAttribute('href', '/jobs');
  });

  it('shows an error state with a retry when the list fails to load', async () => {
    listMock.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'boom', {}, 'trace-9'));

    renderWithProviders(<TrackedCompaniesPage />, { user: testUser });

    expect(await screen.findByText('Unable to load your tracked companies.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('removes a card as soon as it is untracked, without refetching', async () => {
    listMock.mockResolvedValue(page([alpha, beta]));
    untrackMock.mockResolvedValue(undefined);

    renderWithProviders(<TrackedCompaniesPage />, { user: testUser });

    fireEvent.click(await screen.findByRole('button', { name: /Untrack Alpha Systems/ }));

    // Alpha's card is gone, Beta's is still there, and the count followed.
    expect(await screen.findByText('You track 1 company.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Alpha Systems' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Beta Industries' })).toBeInTheDocument();
    expect(untrackMock).toHaveBeenCalledWith(alpha.companyId);
    // Still the one initial load: the server confirmed the delete, so the list
    // already knew the answer.
    expect(listMock).toHaveBeenCalledTimes(1);
  });

  it('does not ask for each card\'s status, because every row is tracked', async () => {
    listMock.mockResolvedValue(page([alpha, beta]));

    renderWithProviders(<TrackedCompaniesPage />, { user: testUser });

    await screen.findByRole('link', { name: 'Alpha Systems' });
    expect(trackingApi.status).not.toHaveBeenCalled();
  });
});
