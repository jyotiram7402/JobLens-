import { Link, Outlet } from 'react-router-dom';
import { AppNav } from './AppNav';

/**
 * The application shell: header, navigation, content.
 *
 * <p>Used by every route, so the chrome is defined once and a new page only
 * provides its content.
 *
 * <p>The layout is a CSS grid that collapses to a single column on narrow
 * screens, with the navigation moving under the header. Mobile matters here
 * beyond the usual reasons: the Scan feature is a phone camera pointed at a
 * building, so the small screen is the primary one for it rather than an
 * afterthought.
 */
export function AppLayout() {
  return (
    <div className="app-shell">
      {/*
        Lets a keyboard user jump past the navigation. Visually hidden until
        focused, which is the point -- the people who need it are the people
        who will find it.
      */}
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>

      <header className="app-header">
        <Link to="/" className="app-brand">
          JobLens
        </Link>
        <span className="app-tagline">See a company. Discover the opportunity.</span>
      </header>

      <div className="app-body">
        <AppNav />

        {/* tabIndex -1 so the skip link can move focus here, not just scroll. */}
        <main className="app-main" id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
