import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Input } from './Input';

describe('Input', () => {
  it('associates the label with the control', () => {
    render(<Input label="Email address" />);

    // Finding by label text only works if the two are genuinely associated,
    // which is exactly the thing worth testing.
    expect(screen.getByLabelText('Email address')).toBeInTheDocument();
  });

  it('marks the field invalid and announces the error', () => {
    render(<Input label="Email address" error="must be a valid email address" />);

    expect(screen.getByLabelText('Email address')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('must be a valid email address');
  });

  it('is not marked invalid without an error', () => {
    render(<Input label="Email address" />);

    expect(screen.getByLabelText('Email address')).not.toHaveAttribute('aria-invalid');
  });

  it('describes the field with its hint', () => {
    render(<Input label="Password" hint="Between 10 and 72 characters." />);

    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
      'Between 10 and 72 characters.',
    );
  });
});
