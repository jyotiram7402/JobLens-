import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderWithProviders, testUser } from '../../../test/render';
import { trackingApi } from '../api';
import { TrackButton } from './TrackButton';

vi.mock('../api', () => ({
  trackingApi: {
    track: vi.fn(),
    untrack: vi.fn(),
    status: vi.fn(),
    list: vi.fn(),
  },
}));

const trackMock = vi.mocked(trackingApi.track);
const untrackMock = vi.mocked(trackingApi.untrack);
const statusMock = vi.mocked(trackingApi.status);

const COMPANY_ID = '33333333-3333-3333-3333-333333333333';

function status(tracked: boolean) {
  return {
    companyId: COMPANY_ID,
    tracked,
    trackedAt: tracked ? new Date().toISOString() : null,
  };
}

function renderButton(options: { signedIn?: boolean; initialTracked?: boolean } = {}) {
  const onChange = vi.fn();
  renderWithProviders(
    <TrackButton
      companyId={COMPANY_ID}
      companyName="Example Company"
      initialTracked={options.initialTracked}
      onChange={onChange}
    />,
    { user: options.signedIn === false ? null : testUser },
  );
  return { onChange };
}

describe('TrackButton', () => {
  beforeEach(() => {
    trackMock.mockReset();
    untrackMock.mockReset();
    statusMock.mockReset();
  });

  it('invites a signed-out visitor to sign in instead of offering to track', () => {
    renderButton({ signedIn: false });

    expect(screen.getByRole('link', { name: 'Sign in to track' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(statusMock).not.toHaveBeenCalled();
  });

  it('loads the current state and offers to track an untracked company', async () => {
    statusMock.mockResolvedValue(status(false));

    renderButton();

    // The company name is in the accessible name, so a screen reader user
    // knows which company the button acts on.
    expect(await screen.findByRole('button', { name: /Track company Example Company/ }))
      .toBeEnabled();
  });

  it('shows the tracked state in words, not only colour', async () => {
    statusMock.mockResolvedValue(status(true));

    renderButton();

    expect(await screen.findByText('Tracked')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Untrack/ })).toBeInTheDocument();
  });

  it('tracks, then shows the tracked state and announces it', async () => {
    statusMock.mockResolvedValue(status(false));
    trackMock.mockResolvedValue(status(true));

    const { onChange } = renderButton();

    fireEvent.click(await screen.findByRole('button', { name: /Track company/ }));

    expect(await screen.findByText('Tracked')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Now tracking Example Company.');
    expect(trackMock).toHaveBeenCalledWith(COMPANY_ID);
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('untracks, then offers to track again', async () => {
    untrackMock.mockResolvedValue(undefined);

    const { onChange } = renderButton({ initialTracked: true });

    fireEvent.click(screen.getByRole('button', { name: /Untrack/ }));

    expect(await screen.findByRole('button', { name: /Track company/ })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Stopped tracking Example Company.');
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('is disabled while a request is in flight, so a double-click sends one request', async () => {
    statusMock.mockResolvedValue(status(false));
    // Never resolves: the request stays in flight for the rest of the test.
    trackMock.mockReturnValue(new Promise(() => undefined));

    renderButton();

    const button = await screen.findByRole('button', { name: /Track company/ });
    fireEvent.click(button);
    fireEvent.click(button);

    expect(button).toBeDisabled();
    expect(trackMock).toHaveBeenCalledTimes(1);
  });

  it('skips the status request when the caller already knows the state', () => {
    renderButton({ initialTracked: true });

    expect(statusMock).not.toHaveBeenCalled();
    expect(screen.getByText('Tracked')).toBeInTheDocument();
  });

  it('shows an error and keeps the previous state when tracking fails', async () => {
    statusMock.mockResolvedValue(status(false));
    trackMock.mockRejectedValue(new Error('network'));

    renderButton();

    fireEvent.click(await screen.findByRole('button', { name: /Track company/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not track this company');
    // Still untracked: nothing claimed a success that did not happen.
    expect(screen.queryByText('Tracked')).not.toBeInTheDocument();
  });
});
