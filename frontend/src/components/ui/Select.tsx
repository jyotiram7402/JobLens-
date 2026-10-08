import { useId } from 'react';
import type { SelectHTMLAttributes } from 'react';

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: string;
  options: SelectOption[];
  /** Shown as the first entry with an empty value, e.g. "Any work mode". */
  placeholder?: string;
}

/**
 * A labelled native `<select>`.
 *
 * <p>Native on purpose. A custom dropdown means reimplementing keyboard
 * navigation, typeahead, focus trapping and the mobile picker, and getting all
 * four right is a lot of work to look slightly different. The filters this will
 * carry are short lists where the native control is simply better.
 */
export function Select({ label, options, placeholder, className, ...rest }: SelectProps) {
  const id = useId();

  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <select
        id={id}
        className={['field-input', className].filter(Boolean).join(' ')}
        {...rest}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}
