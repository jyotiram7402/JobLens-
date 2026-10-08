import { Spinner } from './Spinner';

interface LoadingStateProps {
  /** What is being fetched, e.g. "Loading jobs". */
  message?: string;
}

/**
 * The standard "fetching" block.
 *
 * <p>One of three states every data-backed view needs. Having them as
 * components rather than ad-hoc markup is what stops each page inventing its
 * own, and what makes "the page is loading" look the same everywhere.
 */
export function LoadingState({ message = 'Loading' }: LoadingStateProps) {
  return (
    <div className="state-block">
      <Spinner label={message} />
      <p className="state-message">{message}…</p>
    </div>
  );
}
