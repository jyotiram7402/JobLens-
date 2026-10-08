import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthContextForTests } from '../features/auth/AuthContext';
import type { CurrentUser } from '../features/profile/types';

/**
 * Test helpers.
 *
 * <p>Almost every component here needs routing context, and several need an
 * authenticated user. Wrapping that up once keeps each test about the thing it
 * is testing rather than about its scaffolding.
 */

export const testProfile: CurrentUser['profile'] = {
  headline: 'Software Engineer',
  summary: null,
  yearsOfExperience: 3,
  currentRole: 'Software Engineer',
  remotePreference: 'HYBRID',
  preferredRoles: ['Java Backend Developer'],
  preferredLocations: ['Pune'],
  skills: ['Java', 'Spring Boot'],
};

export const testUser: CurrentUser = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  profile: testProfile,
};

interface Options {
  /** Null renders as signed out. */
  user?: CurrentUser | null;
  /** Initial URL, for pages that read route or query parameters. */
  route?: string;
}

/**
 * Renders a component inside a router and an auth context.
 *
 * <p>The auth value is supplied directly rather than going through
 * `AuthProvider`, so a test does not have to stub the `/users/me` call just to
 * render a signed-in page.
 */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { user = null, route = '/' } = options;

  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthContextForTests.Provider
        value={{
          user,
          initialising: false,
          login: async () => undefined,
          register: async () => undefined,
          logout: () => undefined,
          refresh: async () => undefined,
        }}
      >
        {ui}
      </AuthContextForTests.Provider>
    </MemoryRouter>,
  );
}
