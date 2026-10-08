import { NavLink } from 'react-router-dom';

interface NavItem {
  to: string;
  label: string;
  /** Hidden from signed-out visitors, because the page would redirect anyway. */
  requiresAuth: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', requiresAuth: true },
  { to: '/jobs', label: 'Jobs', requiresAuth: false },
  { to: '/profile', label: 'Profile', requiresAuth: true },
  { to: '/scan', label: 'Scan', requiresAuth: true },
];

interface AppNavProps {
  signedIn: boolean;
  /** Closes the mobile menu after navigating. */
  onNavigate?: () => void;
}

export function AppNav({ signedIn, onNavigate }: AppNavProps) {
  const items = NAV_ITEMS.filter((item) => signedIn || !item.requiresAuth);

  return (
    // Labelled, because a page can have several navigation regions and a screen
    // reader user needs to tell them apart. NavLink sets aria-current="page"
    // itself, so the current page is announced and not only highlighted.
    <nav className="app-nav" aria-label="Main">
      <ul className="app-nav-list">
        {items.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              onClick={onNavigate}
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
