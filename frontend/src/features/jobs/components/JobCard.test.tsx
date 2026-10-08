import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../../../test/render';
import { JobCard } from './JobCard';
import type { JobSummary } from '../types';

const job: JobSummary = {
  id: '22222222-2222-2222-2222-222222222222',
  title: 'Java Backend Developer',
  company: {
    id: '33333333-3333-3333-3333-333333333333',
    name: 'Example Company',
    slug: 'example-company',
  },
  location: 'Pune, Maharashtra, India',
  employmentType: 'FULL_TIME',
  workMode: 'HYBRID',
  experienceMin: 1,
  experienceMax: 4,
  postedAt: new Date(Date.now() - 2 * 86_400_000).toISOString(),
  active: true,
};

describe('JobCard', () => {
  it('shows the title, company and the facts a reader scans for', () => {
    renderWithProviders(<JobCard job={job} />);

    expect(screen.getByRole('heading', { name: 'Java Backend Developer' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Example Company' })).toBeInTheDocument();
    expect(screen.getByText('Pune, Maharashtra, India')).toBeInTheDocument();
    expect(screen.getByText('Hybrid')).toBeInTheDocument();
    expect(screen.getByText('Full time')).toBeInTheDocument();
    expect(screen.getByText('1-4 years')).toBeInTheDocument();
  });

  it('links the title to the job and the company to the company', () => {
    renderWithProviders(<JobCard job={job} />);

    expect(screen.getByRole('link', { name: 'Java Backend Developer' })).toHaveAttribute(
      'href',
      `/jobs/${job.id}`,
    );
    expect(screen.getByRole('link', { name: 'Example Company' })).toHaveAttribute(
      'href',
      `/companies/${job.company.id}`,
    );
  });

  it('shows the posting age rather than a raw date', () => {
    renderWithProviders(<JobCard job={job} />);

    expect(screen.getByText(/2 days ago/)).toBeInTheDocument();
  });

  it('shows a match score when one is supplied', () => {
    renderWithProviders(<JobCard job={job} matchScore={88} />);

    // The number and the word, not a coloured ring: colour alone is unreadable
    // for some users and meaningless to a screen reader.
    expect(screen.getByText('88%')).toBeInTheDocument();
    expect(screen.getByText(/match/)).toBeInTheDocument();
  });

  it('shows no match score when none is supplied', () => {
    renderWithProviders(<JobCard job={job} />);

    expect(screen.queryByText(/match/)).not.toBeInTheDocument();
  });

  it('says a job is not scored rather than showing a misleading zero', () => {
    renderWithProviders(<JobCard job={job} matchScore={0} matchScored={false} />);

    expect(screen.getByText('Not scored')).toBeInTheDocument();
    expect(screen.queryByText('0%')).not.toBeInTheDocument();
  });

  it('omits experience entirely when the employer did not state it', () => {
    // Null means unstated, and rendering "0 years" would invent a claim the
    // backend deliberately avoided making.
    renderWithProviders(
      <JobCard job={{ ...job, experienceMin: null, experienceMax: null }} />,
    );

    expect(screen.queryByText(/years/)).not.toBeInTheDocument();
  });

  it('shows no skills section when none are provided', () => {
    // Search results carry no skills, and inventing them in the frontend would
    // be showing the user something the backend never said.
    renderWithProviders(<JobCard job={job} />);

    expect(screen.queryByRole('list', { name: /skills/i })).not.toBeInTheDocument();
  });

  it('shows skills when they are provided', () => {
    renderWithProviders(<JobCard job={job} skills={['Java', 'Docker']} />);

    expect(screen.getByText('Java')).toBeInTheDocument();
    expect(screen.getByText('Docker')).toBeInTheDocument();
  });
});
