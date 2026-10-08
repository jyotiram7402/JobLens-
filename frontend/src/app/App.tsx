import { RouterProvider } from 'react-router-dom';
import { ErrorBoundary } from './providers/ErrorBoundary';
import { router } from './router';

/**
 * The application root.
 *
 * <p>Everything that wraps the whole app goes here — currently the error
 * boundary and the router. Keeping it separate from `main.tsx` means tests can
 * render the app without touching the DOM bootstrap.
 *
 * <p>No state-management provider, deliberately. See ARCHITECTURE.md: React
 * state and a little Context are enough for what this application does today,
 * and a store adopted before there is shared state to put in it is a set of
 * conventions without a problem.
 */
export function App() {
  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  );
}
