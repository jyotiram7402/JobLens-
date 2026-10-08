import type { ReactNode } from 'react';

type Tone = 'neutral' | 'positive' | 'caution';

interface BadgeProps {
  children: ReactNode;
  tone?: Tone;
  /**
   * What the badge means, when the text alone is not self-explanatory. Colour
   * must never be the only thing carrying meaning -- for colour-blind users and
   * for screen readers it carries none at all.
   */
  label?: string;
}

/** A small label for a job's work mode, employment type or status. */
export function Badge({ children, tone = 'neutral', label }: BadgeProps) {
  return (
    <span className={`badge badge-${tone}`}>
      {label && <span className="sr-only">{label}: </span>}
      {children}
    </span>
  );
}
