import { useEffect, useState } from 'react';

/**
 * Returns a value that only updates once it has stopped changing.
 *
 * <p>Used for the job search box. Without it, every keystroke is a request:
 * typing "java" fires four searches, three of which are thrown away, and on a
 * sleeping free-tier backend they may not even come back in order.
 *
 * @param delayMs 300ms by default — long enough to collapse a burst of typing,
 *                short enough that the results do not feel detached from it
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    // Clearing on every change is what makes it a debounce rather than a
    // throttle: the timer restarts while the user is still typing.
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
