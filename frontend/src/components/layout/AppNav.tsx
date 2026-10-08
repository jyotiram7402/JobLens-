import { NavLink } from 'react-router-dom';

interface NavItem {
  to: string;
  label: string;
}

/**
 * The application's primary navigation.
 *
 * <p>Everything except Home needs an account. The links are shown regardless
 * for now, because authentication is not wired up yet and hiding them would
 * leave nothing to navigate. Once sign-in works this list is filtered by it --
 * one place to change.
 */
const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/jobs', label: 'Jobs' },
  { to: '/profile', label: 'Profile' },
  { to: '/scan', label: 'Scan' },
];

export function AppNav() {
  return (
    // <nav> with a label, because a page can have several navigation regions
    // and a screen reader user needs to tell them apart. NavLink sets
    // aria-current="page" on the active link by itself, so the current page is
    // announced and not only highlighted.
    <nav className="app-nav" aria-label="Main">
      <ul className="app-nav-list">
        {NAV_ITEMS.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              className={({ isActive }) =>
                isActive ? 'app-nav-link app-nav-link-active' : 'app-nav-link'
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
