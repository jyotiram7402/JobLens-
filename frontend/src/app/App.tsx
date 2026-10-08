import { RouterProvider } from 'react-router-dom';
import { ErrorBoundary } from './providers/ErrorBoundary';
import { AuthProvider } from '../features/auth/AuthContext';
import { router } from './router';

/**
 * The application root.
 *
 * <p>Order matters: the error boundary is outermost, so a failure inside the
 * auth provider or the router still renders something rather than a blank page.
 *
 * <p>`AuthProvider` is the only context here, and it exists because the signed-in
 * user is genuinely needed in several places — the header, the dashboard, the
 * route guard, the match panel. Still not a store: one shared value is not a
 * state-management problem.
 */
export function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </ErrorBoundary>
  );
}
