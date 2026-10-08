interface SpinnerProps {
  /** Announced to assistive technology. Defaults to "Loading". */
  label?: string;
}

/**
 * A busy indicator.
 *
 * <p>`role="status"` makes it announced rather than purely decorative, and the
 * CSS respects `prefers-reduced-motion` -- a spinning element is a genuine
 * problem for some people.
 */
export function Spinner({ label = 'Loading' }: SpinnerProps) {
  return (
    <span className="spinner" role="status">
      <span className="sr-only">{label}</span>
    </span>
  );
}
