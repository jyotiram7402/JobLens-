import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { HomePage } from './HomePage';

/**
 * An example page-rendering test.
 *
 * <p>`fetch` is stubbed rather than mocking the API client, so the test covers
 * the real client, the real error translation and the real component together.
 * `MemoryRouter` supplies the routing context the page's links need without a
 * browser history.
 */
describe('HomePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function renderPage() {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );
  }

  it('renders exactly one page heading', () => {
    renderPage();

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'See a company. Discover the opportunity.',
    );
  });

  it('offers the main entry points', () => {
    renderPage();

    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute(
      'href',
      '/register',
    );
    expect(screen.getByRole('link', { name: 'Browse jobs' })).toHaveAttribute('href', '/jobs');
  });

  it('reports an unreachable API instead of rendering nothing', async () => {
    renderPage();

    // The whole point of the environment check: a failure must be visible on
    // the page, not only in the console.
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
