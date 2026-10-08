import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  /** Shows a spinner and disables the button. */
  loading?: boolean;
  children: ReactNode;
}

/**
 * A real `<button>`, always.
 *
 * <p>A styled `<div>` with an onClick is not keyboard focusable, is not
 * announced as a control, and does not fire on Enter or Space. Those are not
 * details to add later; they are what a button is.
 *
 * <p>`type` defaults to `button`. The HTML default is `submit`, which means any
 * button inside a form silently submits it — a bug that only shows up once
 * forms exist.
 */
export function Button({
  variant = 'primary',
  loading = false,
  disabled,
  type = 'button',
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={['btn', `btn-${variant}`, className].filter(Boolean).join(' ')}
      disabled={disabled || loading}
      // Tells a screen reader the control is working rather than broken.
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <span className="btn-spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}
