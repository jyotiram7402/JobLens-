import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, testUser } from '../../../test/render';
import { ApiError } from '../../../services/api/ApiError';
import { jobsApi } from '../api';
import { matchingApi } from '../../matching/api';
import { JobDetailPage } from './JobDetailPage';
import type { Job } from '../types';
import type { JobMatch } from '../../matching/types';

vi.mock('../api', () => ({
  jobsApi: { search: vi.fn(), byId: vi.fn(), byCompany: vi.fn() },
}));

vi.mock('../../matching/api', () => ({
  matchingApi: { forJob: vi.fn(), recommended: vi.fn() },
}));

const byIdMock = vi.mocked(jobsApi.byId);
const matchMock = vi.mocked(matchingApi.forJob);

const JOB_ID = '22222222-2222-2222-2222-222222222222';

const job: Job = {
  id: JOB_ID,
  title: 'Java Backend Developer',
  company: { id: '33333333-3333-3333-3333-333333333333', name: 'Example Company', slug: 'example-company' },
  description: 'Build and maintain REST services.',
  location: 'Pune, Maharashtra, India',
  employmentType: 'FULL_TIME',
  workMode: 'HYBRID',
  experienceMin: 2,
  experienceMax: 5,
  applyUrl: 'https://example.com/careers/123',
  skills: ['Java', 'Spring Boot', 'PostgreSQL'],
  postedAt: new Date().toISOString(),
  active: true,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const match: JobMatch = {
  jobId: JOB_ID,
  scored: true,
  score: 88,
  maxScore: 100,
  breakdown: {
    skills: {
      score: 38, maxScore: 50, applicable: true, matched: true,
      reason: 'You have 3 of the 4 skills this job asks for.',
      matchedSkills: ['Java', 'Spring Boot'],
      missingSkills: ['PostgreSQL'],
    },
    experience: { score: 20, maxScore: 20, applicable: true, matched: true, reason: 'Fits.' },
    location: { score: 15, maxScore: 15, applicable: true, matched: true, reason: 'Matches.' },
    role: { score: 10, maxScore: 10, applicable: true, matched: true, reason: 'Matches.' },
    workMode: { score: 0, maxScore: 0, applicable: false, matched: false, reason: 'Not stated.' },
  },
  explanation: ['This job scores 88 out of 100.', 'The one skill gap is PostgreSQL.'],
};

function renderPage(options: { signedIn?: boolean } = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="/jobs/:jobId" element={<JobDetailPage />} />
    </Routes>,
    { route: `/jobs/${JOB_ID}`, user: options.signedIn ? testUser : null },
  );
}

describe('JobDetailPage', () => {
  beforeEach(() => {
    byIdMock.mockReset();
    matchMock.mockReset();
  });

  it('shows the job and its details', async () => {
    byIdMock.mockResolvedValue(job);

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Java Backend Developer', level: 1 }))
      .toBeInTheDocument();
    expect(screen.getByText('Build and maintain REST services.')).toBeInTheDocument();
    expect(screen.getAllByText(/Pune/).length).toBeGreaterThan(0);
    expect(screen.getByText('2-5 years')).toBeInTheDocument();
    expect(screen.getByText('PostgreSQL')).toBeInTheDocument();
  });

  it('links Apply to the employer, safely and in a new tab', async () => {
    byIdMock.mockResolvedValue(job);

    renderPage();

    const apply = await screen.findByRole('link', { name: /Apply for this job/ });
    expect(apply).toHaveAttribute('href', 'https://example.com/careers/123');
    // noopener matters: without it the opened page can navigate this one.
    expect(apply).toHaveAttribute('rel', 'noopener noreferrer');
    expect(apply).toHaveAttribute('target', '_blank');
  });

  it('shows job not found rather than crashing on a 404', async () => {
    byIdMock.mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', 'No job found'));

    renderPage();

    expect(await screen.findByText('Job not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to jobs' })).toBeInTheDocument();
  });

  it('invites an anonymous visitor to sign in for their match', async () => {
    byIdMock.mockResolvedValue(job);

    renderPage({ signedIn: false });

    expect(await screen.findByText(/Sign in to see how this job scores/)).toBeInTheDocument();
    expect(matchMock).not.toHaveBeenCalled();
  });

  it('shows the score, the breakdown and the backend explanation', async () => {
    byIdMock.mockResolvedValue(job);
    matchMock.mockResolvedValue(match);

    renderPage({ signedIn: true });

    expect(await screen.findByText('88%')).toBeInTheDocument();
    expect(screen.getByText('38/50')).toBeInTheDocument();
    expect(screen.getByText('20/20')).toBeInTheDocument();
    // An inapplicable criterion is not a zero: it was excluded from the total
    // along with its weight, and must not read as a failure.
    expect(screen.getByText('not compared')).toBeInTheDocument();
    expect(screen.getByText('The one skill gap is PostgreSQL.')).toBeInTheDocument();
  });

  it('separates the skills the user has from the ones they lack', async () => {
    byIdMock.mockResolvedValue(job);
    matchMock.mockResolvedValue(match);

    renderPage({ signedIn: true });

    expect(await screen.findByText('Skills you have')).toBeInTheDocument();
    expect(screen.getByText('Skills to gain')).toBeInTheDocument();
  });

  it('asks an incomplete profile to be completed instead of showing a fake score', async () => {
    byIdMock.mockResolvedValue(job);
    matchMock.mockRejectedValue(
      new ApiError(422, 'PROFILE_NOT_READY', 'Add some skills…'),
    );

    renderPage({ signedIn: true });

    expect(await screen.findByText(/Complete your profile/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Complete profile' })).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it('keeps the job readable when matching fails entirely', async () => {
    // Matching is the part most likely to be unavailable, and none of its
    // failure modes is a reason to stop someone reading the job.
    byIdMock.mockResolvedValue(job);
    matchMock.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'boom'));

    renderPage({ signedIn: true });

    expect(await screen.findByRole('heading', { name: 'Java Backend Developer', level: 1 }))
      .toBeInTheDocument();
    expect(screen.getByText(/match score is unavailable/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Apply for this job/ })).toBeInTheDocument();
  });
});
