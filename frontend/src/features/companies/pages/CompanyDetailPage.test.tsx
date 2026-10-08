import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '../../../test/render';
import { ApiError } from '../../../services/api/ApiError';
import { companiesApi } from '../api';
import { jobsApi } from '../../jobs/api';
import { CompanyDetailPage } from './CompanyDetailPage';
import type { Company } from '../types';
import type { JobSummary } from '../../jobs/types';

vi.mock('../api', () => ({ companiesApi: { byId: vi.fn() } }));
vi.mock('../../jobs/api', () => ({
  jobsApi: { search: vi.fn(), byId: vi.fn(), byCompany: vi.fn() },
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

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/companies/:companyId" element={<CompanyDetailPage />} />
    </Routes>,
    { route: `/companies/${COMPANY_ID}` },
  );
}

describe('CompanyDetailPage', () => {
  beforeEach(() => {
    companyMock.mockReset();
    jobsMock.mockReset();
    jobsMock.mockResolvedValue(emptyPage());
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
});
