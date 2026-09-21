import { Link, Outlet } from 'react-router-dom';

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="app-brand">
          JobLens
        </Link>
        <span className="app-tagline">See a company. Discover the opportunity.</span>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">JobLens V1 — foundation</footer>
    </div>
  );
}
