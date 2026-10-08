/**
 * Display helpers. Pure functions, so they are trivially testable.
 */

/**
 * "today", "2 days ago", "3 weeks ago", then an absolute date.
 *
 * <p>Relative time is what matters for a job posting — "2 days ago" tells you
 * it is fresh in a way that "6 October" does not. Beyond a month the relative
 * form stops being useful and an actual date is clearer.
 */
export function formatRelativeDate(isoDate: string): string {
  const then = new Date(isoDate);
  if (Number.isNaN(then.getTime())) {
    return '';
  }

  const days = Math.floor((Date.now() - then.getTime()) / 86_400_000);

  if (days < 0) {
    return 'just now';
  }
  if (days === 0) {
    return 'today';
  }
  if (days === 1) {
    return 'yesterday';
  }
  if (days < 7) {
    return `${days} days ago`;
  }
  if (days < 30) {
    const weeks = Math.floor(days / 7);
    return weeks === 1 ? 'last week' : `${weeks} weeks ago`;
  }
  return then.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

/**
 * "1-4 years", "3+ years", "up to 4 years", or null when unstated.
 *
 * <p>Returns null rather than "0 years" or "Not specified" for an unstated
 * range, so the caller can leave the field out entirely. The backend treats
 * null as "the employer did not say", and the UI should not turn that into a
 * claim.
 */
export function formatExperienceRange(min: number | null, max: number | null): string | null {
  if (min === null && max === null) {
    return null;
  }
  if (min !== null && max !== null) {
    return min === max ? `${min} years` : `${min}-${max} years`;
  }
  if (min !== null) {
    return `${min}+ years`;
  }
  return `up to ${max} years`;
}

/**
 * Initials for a company with no logo — "Example Company" becomes "EC".
 *
 * <p>Generated locally rather than fetched from a logo service: that would be
 * an external dependency, a privacy leak of which companies a user is looking
 * at, and a cost, all to avoid drawing two letters.
 */
export function companyInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

/** "Good morning" / "Good afternoon" / "Good evening", from the local clock. */
export function greeting(now: Date = new Date()): string {
  const hour = now.getHours();
  if (hour < 12) {
    return 'Good morning';
  }
  if (hour < 18) {
    return 'Good afternoon';
  }
  return 'Good evening';
}
