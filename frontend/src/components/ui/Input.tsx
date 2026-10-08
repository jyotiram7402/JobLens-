import { useId } from 'react';
import type { InputHTMLAttributes } from 'react';

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  /** A validation message. Its presence marks the field invalid. */
  error?: string;
  /** Guidance shown under the field, read out with it. */
  hint?: string;
}

/**
 * A labelled text input.
 *
 * <p>The label is required rather than optional, because a placeholder is not a
 * label: it disappears as soon as anyone types, and screen readers do not treat
 * it as a name. `useId` ties the two together without callers inventing unique
 * ids.
 *
 * <p>Errors are wired with `aria-invalid` and `aria-describedby`, so the
 * message is announced rather than merely drawn in red — colour alone does not
 * communicate status.
 */
export function Input({ label, error, hint, className, ...rest }: InputProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  const describedBy = [hint ? hintId : null, error ? errorId : null]
    .filter(Boolean)
    .join(' ');

  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>

      {hint && (
        <p className="field-hint" id={hintId}>
          {hint}
        </p>
      )}

      <input
        id={id}
        className={['field-input', error ? 'field-input-error' : '', className]
          .filter(Boolean)
          .join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        {...rest}
      />

      {error && (
        // role="alert" so the message is announced when it appears.
        <p className="field-error" id={errorId} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
