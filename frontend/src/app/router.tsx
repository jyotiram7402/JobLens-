import { Navigate, Outlet, createBrowserRouter, useLocation } from 'react-router-dom';
import { AppLayout } from '../components/layout/AppLayout';
import { LoadingState } from '../components/ui';
import { useAuth } from '../features/auth/AuthContext';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { DashboardPage } from '../features/dashboard/pages/DashboardPage';
import { JobsPage } from '../features/jobs/pages/JobsPage';
import { JobDetailPage } from '../features/jobs/pages/JobDetailPage';
import { CompanyDetailPage } from '../features/companies/pages/CompanyDetailPage';
import { ProfilePage } from '../features/profile/pages/ProfilePage';
import { ScanPage } from '../features/scan/pages/ScanPage';

/**
 * Sends signed-out visitors to the login page.
 *
 * <p>Waits for the initial session check first. Without that, a page refresh
 * would bounce a signed-in user to `/login` for the moment it takes to confirm
 * their token — which looks exactly like being randomly logged out.
 *
 * <p>Remembers where they were going, so signing in lands on the page they
 * asked for rather than the dashboard.
 *
 * <p><b>This is a convenience, not a security boundary.</b> The backend rejects
 * unauthenticated requests on its own; hiding a route in the browser protects
 * nothing. It exists so people see a sign-in form instead of a page of 401s.
 */
function RequireAuth() {
  const { user, initialising } = useAuth();
  const location = useLocation();

  if (initialising) {
    return <LoadingState message="Loading" />;
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}

/**
 * The route table.
 *
 * <p>Three groups, and the split mirrors the backend's own authorization rules
 * rather than being a separate opinion: job and company browsing is public
 * because discovery is the product, and the backend serves those endpoints to
 * anonymous callers. Only the pages that genuinely need a profile are gated.
 *
 * <p><b>Planned, not yet routed:</b> `/tracked-companies` (step 10). Note that
 * a future `/jobs/recommended` route would have to be declared <em>before</em>
 * `/jobs/:jobId`, or "recommended" is parsed as a job id — recommendations
 * currently live on the dashboard instead.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      // Public.
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },

      // Public: discovery works without an account, as it does on the backend.
      { path: 'jobs', element: <JobsPage /> },
      { path: 'jobs/:jobId', element: <JobDetailPage /> },
      { path: 'companies/:companyId', element: <CompanyDetailPage /> },

      // Requires an account.
      {
        element: <RequireAuth />,
        children: [
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'scan', element: <ScanPage /> },
        ],
      },

      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
