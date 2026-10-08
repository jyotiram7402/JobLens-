import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Button } from '../../../components/ui';
import { useAuth } from '../../auth/AuthContext';
import { trackingApi } from '../api';

interface TrackButtonProps {
  companyId: string;
  /** Used in the accessible name, so "Track company" says which company. */
  companyName: string;
  /**
   * The known state, when the caller already has it — the tracked-companies
   * list knows every row is tracked. Supplying it skips the status request.
   */
  initialTracked?: boolean;
  /** Told about every successful change, e.g. to remove a card from a list. */
  onChange?: (tracked: boolean) => void;
}

/**
 * Track or untrack a company.
 *
 * <p>Two visibly different states rather than one toggle whose label flips.
 * "✓ Tracked" plus an explicit "Untrack" button cannot be misread: a toggle
 * labelled "Tracked" leaves the user guessing whether pressing it will track or
 * untrack, and the tick plus the word means the state never depends on colour.
 *
 * <p>The state changes only after the server confirms, not optimistically. The
 * request is fast and idempotent, so the honest version costs a few hundred
 * milliseconds — and an optimistic tick that later reverts is exactly the kind
 * of flicker that makes people distrust a button.
 *
 * <p>The button is disabled while a request is in flight, which is what stops a
 * double-click from sending two. The backend would handle two anyway — tracking
 * is idempotent and the database rejects duplicates — but a second request
 * that cannot change anything is still not worth sending.
 *
 * <p>Changes are announced through a live region that is always present. A
 * live region that only appears when there is something to say is often not
 * announced at all, because screen readers watch regions that already exist.
 */
export function TrackButton({
  companyId,
  companyName,
  initialTracked,
  onChange,
}: TrackButtonProps) {
  const { user } = useAuth();
  const location = useLocation();
  const signedIn = Boolean(user);

  const [tracked, setTracked] = useState<boolean | null>(initialTracked ?? null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');

  // Ask the server only when the state is not already known. On the company
  // page it is not; in the tracked list it always is.
  useEffect(() => {
    if (!signedIn || initialTracked !== undefined) {
      return;
    }

    const controller = new AbortController();

    trackingApi
      .status(companyId, controller.signal)
      .then((status) => setTracked(status.tracked))
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === 'AbortError') {
          return;
        }
        setError('Could not load whether you track this company.');
      });

    return () => controller.abort();
  }, [companyId, signedIn, initialTracked]);

  if (!signedIn) {
    // Carries the current page, so signing in comes straight back here.
    return (
      <Link className="btn btn-secondary" to="/login" state={{ from: location.pathname }}>
        Sign in to track
      </Link>
    );
  }

  async function handleClick() {
    setPending(true);
    setError(null);

    try {
      if (tracked) {
        await trackingApi.untrack(companyId);
        setTracked(false);
        setAnnouncement(`Stopped tracking ${companyName}.`);
        onChange?.(false);
      } else {
        const status = await trackingApi.track(companyId);
        setTracked(status.tracked);
        setAnnouncement(`Now tracking ${companyName}.`);
        onChange?.(status.tracked);
      }
    } catch {
      setError(
        tracked
          ? 'Could not untrack this company. Please try again.'
          : 'Could not track this company. Please try again.',
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="track-control">
      {tracked ? (
        <>
          <span className="track-state">
            <span aria-hidden="true">✓ </span>
            Tracked
          </span>
          <Button variant="secondary" loading={pending} onClick={handleClick}>
            Untrack
            {/* Visible text first, so the accessible name still contains what
                is on screen; the company name says which one. */}
            <span className="sr-only"> {companyName}</span>
          </Button>
        </>
      ) : (
        <Button loading={pending} disabled={tracked === null} onClick={handleClick}>
          Track company
          <span className="sr-only"> {companyName}</span>
        </Button>
      )}

      <span className="sr-only" role="status" aria-live="polite">
        {announcement}
      </span>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
