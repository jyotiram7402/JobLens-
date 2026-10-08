import { createBrowserRouter, Outlet } from 'react-router-dom';
import { AppLayout } from '../components/layout/AppLayout';
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
 * Where the authentication guard will go.
 *
 * <p>Routes that need an account are nested under this element, so adding the
 * guard later is an edit to one component rather than to every route. The
 * grouping is the point: it makes "which pages need a login?" answerable by
 * looking at the route table.
 *
 * <p>It currently renders its children unchanged, on purpose. Sign-in is not
 * connected yet, so redirecting to `/login` would make every page unreachable
 * with no way back in — a guard that locks out the only people who could test
 * it. The check is one line, and the next step adds it:
 *
 * <pre>
 *   if (!tokenStorage.has()) {
 *     return &lt;Navigate to="/login" replace state={{ from: location }} /&gt;;
 *   }
 * </pre>
 *
 * <p>Note that this is a convenience, not a security boundary. The backend
 * rejects unauthenticated requests on its own; hiding a route in the browser
 * stops nothing, and the server is what actually protects the data.
 */
function ProtectedRoutes() {
  return <Outlet />;
}

/**
 * The route table.
 *
 * <p>Split into public and protected groups. Adding a page means adding one
 * entry under the right group.
 *
 * <p><b>Planned, not yet routed:</b>
 * <ul>
 *   <li>{@code /tracked-companies} — step 10</li>
 *   <li>{@code /jobs/recommended} — step 9, fed by the matching API that
 *       already exists. Note it must be declared <em>before</em>
 *       {@code /jobs/:jobId} or "recommended" is read as an id.</li>
 *   <li>{@code /jobs/:jobId/match} — likely a section of the job detail page
 *       rather than its own route, since the data comes with the job.</li>
 * </ul>
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

      // Requires an account.
      {
        element: <ProtectedRoutes />,
        children: [
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'jobs', element: <JobsPage /> },
          { path: 'jobs/:jobId', element: <JobDetailPage /> },
          { path: 'companies/:companyId', element: <CompanyDetailPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'scan', element: <ScanPage /> },
        ],
      },

      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
