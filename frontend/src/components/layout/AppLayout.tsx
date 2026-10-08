import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AppNav } from './AppNav';
import { useAuth } from '../../features/auth/AuthContext';

/**
 * The application shell: header, navigation, content.
 *
 * <p>A CSS grid that collapses to one column on narrow screens, where the
 * navigation becomes a toggled menu rather than a permanent sidebar. Mobile
 * matters here beyond the usual reasons: Scan is a phone camera pointed at a
 * building, so the small screen is the primary one for it.
 */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  function handleLogout() {
    logout();
    navigate('/', { replace: true });
  }

  return (
    <div className="app-shell">
      {/* Lets a keyboard user jump past the navigation. Hidden until focused,
          which is the point: the people who need it will find it. */}
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>

      <header className="app-header">
        <Link to="/" className="app-brand">
          JobLens
        </Link>

        <div className="app-header-actions">
          {user ? (
            <>
              <Link className="app-header-user" to="/profile">
                {user.firstName} {user.lastName}
              </Link>
              <button type="button" className="btn btn-ghost" onClick={handleLogout}>
                Sign out
              </button>
            </>
          ) : (
            // The login link carries where the visitor currently is, so signing
            // in returns them there instead of to the dashboard.
            <>
              <Link className="btn btn-ghost" to="/login" state={{ from: location.pathname }}>
                Sign in
              </Link>
              <Link className="btn btn-primary" to="/register">
                Create account
              </Link>
            </>
          )}

          {/* Only rendered on narrow screens, where the sidebar collapses.
              aria-expanded and aria-controls tell assistive technology what the
              button does and whether the menu is open. */}
          <button
            type="button"
            className="app-menu-toggle"
            aria-expanded={menuOpen}
            aria-controls="app-navigation"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span aria-hidden="true">☰</span>
            <span className="sr-only">{menuOpen ? 'Close menu' : 'Open menu'}</span>
          </button>
        </div>
      </header>

      <div className="app-body">
        <div
          id="app-navigation"
          className={menuOpen ? 'app-nav-wrapper app-nav-wrapper-open' : 'app-nav-wrapper'}
        >
          <AppNav signedIn={Boolean(user)} onNavigate={() => setMenuOpen(false)} />
        </div>

        {/* tabIndex -1 so the skip link can move focus here, not just scroll. */}
        <main className="app-main" id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
