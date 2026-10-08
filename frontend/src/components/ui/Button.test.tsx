import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Button } from './Button';

/**
 * An example component test, and the pattern the rest should follow: assert
 * what a user or a screen reader would observe, not implementation details.
 */
describe('Button', () => {
  it('renders as a real button element', () => {
    render(<Button>Save</Button>);

    // getByRole rather than a class or test id: if this passes, assistive
    // technology sees a button too.
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
  });

  it('defaults to type="button" so it cannot accidentally submit a form', () => {
    render(<Button>Save</Button>);

    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('is disabled and marked busy while loading', () => {
    render(<Button loading>Saving</Button>);

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });

  it('does not fire while loading', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Saving
      </Button>,
    );

    screen.getByRole('button').click();

    expect(onClick).not.toHaveBeenCalled();
  });
});
