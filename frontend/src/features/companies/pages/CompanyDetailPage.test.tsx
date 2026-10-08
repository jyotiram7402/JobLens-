import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, testUser } from '../../../test/render';
import { ApiError } from '../../../services/api/ApiError';
import { companiesApi } from '../api';
import { jobsApi } from '../../jobs/api';
import { trackingApi } from '../../tracking/api';
import { CompanyDetailPage } from './CompanyDetailPage';
import type { Company } from '../types';
import type { JobSummary } from '../../jobs/types';

vi.mock('../api', () => ({ companiesApi: { byId: vi.fn() } }));
vi.mock('../../jobs/api', () => ({
  jobsApi: { search: vi.fn(), byId: vi.fn(), byCompany: vi.fn() },
}));
vi.mock('../../tracking/api', () => ({
  trackingApi: { track: vi.fn(), untrack: vi.fn(), status: vi.fn(), list: vi.fn() },
}));

const companyMock = vi.mocked(companiesApi.byId);
const jobsMock = vi.mocked(jobsApi.byCompany);

const COMPANY_ID = '33333333-3333-3333-3333-333333333333';

const company: Company = {
  id: COMPANY_ID,
  slug: 'example-company',
  name: 'Example Company',
  description: 'Information technology services.',
  websiteUrl: 'https://example.com',
  careersUrl: 'https://example.com/careers',
  logoUrl: null,
  industry: 'Information Technology',
  location: 'Pune, India',
  active: true,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const job: JobSummary = {
  id: '22222222-2222-2222-2222-222222222222',
  title: 'Java Backend Developer',
  company: { id: COMPANY_ID, name: 'Example Company', slug: 'example-company' },
  location: 'Pune',
  employmentType: 'FULL_TIME',
  workMode: 'HYBRID',
  experienceMin: null,
  experienceMax: null,
  postedAt: new Date().toISOString(),
  active: true,
};

function emptyPage() {
  return {
    content: [] as JobSummary[],
    page: 0, size: 5, totalElements: 0, totalPages: 0,
    first: true, last: true, hasNext: false, hasPrevious: false,
  };
}

function renderPage(options: { signedIn?: boolean } = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="/companies/:companyId" element={<CompanyDetailPage />} />
    </Routes>,
    { route: `/companies/${COMPANY_ID}`, user: options.signedIn ? testUser : null },
  );
}

describe('CompanyDetailPage', () => {
  beforeEach(() => {
    companyMock.mockReset();
    jobsMock.mockReset();
    jobsMock.mockResolvedValue(emptyPage());
    // Reset so "not called" assertions do not depend on test order.
    vi.mocked(trackingApi.status).mockReset();
  });

  it('shows the company information', async () => {
    companyMock.mockResolvedValue(company);

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Example Company', level: 1 }))
      .toBeInTheDocument();
    expect(screen.getByText(/Information Technology/)).toBeInTheDocument();
    expect(screen.getByText('Information technology services.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Website/ })).toHaveAttribute(
      'href',
      'https://example.com',
    );
    expect(screen.getByRole('link', { name: /Careers page/ })).toHaveAttribute(
      'rel',
      'noopener noreferrer',
    );
  });

  it('shows initials when the company has no logo', async () => {
    companyMock.mockResolvedValue(company);

    renderPage();

    // Generated locally rather than fetched from a logo service, which would be
    // an external dependency and a leak of what the user is looking at.
    expect(await screen.findByText('EC')).toBeInTheDocument();
  });

  it('lists the company openings', async () => {
    companyMock.mockResolvedValue(company);
    jobsMock.mockResolvedValue({ ...emptyPage(), content: [job], totalElements: 1, totalPages: 1 });

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Open positions' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Java Backend Developer' })).toBeInTheDocument();
  });

  it('fetches the openings through job search, filtered by company', async () => {
    // There is no /companies/{id}/jobs endpoint; this is the ordinary search
    // with one more filter.
    companyMock.mockResolvedValue(company);

    renderPage();

    await screen.findByRole('heading', { name: 'Example Company', level: 1 });
    expect(jobsMock).toHaveBeenCalledWith(COMPANY_ID, expect.anything(), expect.anything());
  });

  it('shows an empty state when the company has no openings', async () => {
    companyMock.mockResolvedValue(company);

    renderPage();

    expect(await screen.findByText('No open positions')).toBeInTheDocument();
  });

  it('shows company not found rather than crashing on a 404', async () => {
    companyMock.mockRejectedValue(new ApiError(404, 'COMPANY_NOT_FOUND', 'No company found'));

    renderPage();

    expect(await screen.findByText('Company not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to jobs' })).toBeInTheDocument();
  });

  it('still shows the company when its openings fail to load', async () => {
    companyMock.mockResolvedValue(company);
    jobsMock.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'boom'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Example Company', level: 1 }))
      .toBeInTheDocument();
    expect(screen.getByText(/Unable to load this company/)).toBeInTheDocument();
  });

  it('invites a signed-out visitor to sign in to track the company', async () => {
    companyMock.mockResolvedValue(company);

    renderPage({ signedIn: false });

    expect(await screen.findByRole('link', { name: 'Sign in to track' })).toBeInTheDocument();
    expect(trackingApi.status).not.toHaveBeenCalled();
  });

  it('offers a signed-in user the track button for this company', async () => {
    companyMock.mockResolvedValue(company);
    vi.mocked(trackingApi.status).mockResolvedValue({
      companyId: COMPANY_ID,
      tracked: false,
      trackedAt: null,
    });

    renderPage({ signedIn: true });

    expect(await screen.findByRole('button', { name: /Track company Example Company/ }))
      .toBeInTheDocument();
    expect(trackingApi.status).toHaveBeenCalledWith(COMPANY_ID, expect.anything());
  });

  it('keeps the company page working when the tracking status cannot be loaded', async () => {
    // Tracking is a side panel. Its failure must never take the page with it.
    companyMock.mockResolvedValue(company);
    vi.mocked(trackingApi.status).mockRejectedValue(new Error('network'));

    renderPage({ signedIn: true });

    expect(await screen.findByRole('heading', { name: 'Example Company', level: 1 }))
      .toBeInTheDocument();
    expect(await screen.findByText(/Could not load whether you track/)).toBeInTheDocument();
  });
});
