import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  /** What to do about it. An empty list without a next step is a dead end. */
  description?: string;
  action?: ReactNode;
}

/**
 * The standard "nothing here" block.
 *
 * <p>Distinct from {@link ErrorState} on purpose: a search that matched nothing
 * is a successful request, and the backend returns 200 with an empty array for
 * exactly that reason. Showing an error would misreport what happened.
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="state-block">
      <p className="state-title">{title}</p>
      {description && <p className="state-message">{description}</p>}
      {action}
    </div>
  );
}
