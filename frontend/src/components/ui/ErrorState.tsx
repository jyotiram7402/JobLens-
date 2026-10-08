import { Button } from './Button';

interface ErrorStateProps {
  /** What went wrong, in plain language. */
  message: string;
  /** Correlation id from an ApiError, worth quoting in a bug report. */
  traceId?: string;
  /** Shows a retry button when supplied. */
  onRetry?: () => void;
}

/**
 * The standard failure block.
 *
 * <p>Shows a message the user can act on and, when there is one, the trace id —
 * which is the same value that appears on every backend log line for the
 * request, so quoting it makes a bug report answerable.
 *
 * <p>Never renders a stack trace or a raw server error. The backend already
 * keeps its internals out of responses; the frontend does not undo that.
 */
export function ErrorState({ message, traceId, onRetry }: ErrorStateProps) {
  return (
    <div className="state-block state-block-error" role="alert">
      <p className="state-message">{message}</p>
      {traceId && (
        <p className="state-detail">
          Reference: <code>{traceId}</code>
        </p>
      )}
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
