import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { tokenStorage } from '../../services/auth/tokenStorage';
import { ApiError } from '../../services/api/ApiError';
import { authApi } from './api';
import type { CurrentUser } from '../profile/types';
import type { LoginRequest, RegisterRequest } from './types';

/**
 * Who is signed in.
 *
 * <p>The one piece of state genuinely shared across the tree: the header shows
 * a name, the dashboard greets by it, the route guard checks it, and the match
 * panel behaves differently without it. Context rather than prop drilling
 * through five levels of layout — and still not a store, because this is the
 * only such value in the application.
 *
 * <p>The user is fetched from `/users/me` on load when a token exists, rather
 * than being decoded from the JWT. The token carries an id, an email and a role
 * and nothing else — no name, no profile — because a signed-but-unencrypted
 * token is a bad place for personal data. One request gets the real thing.
 */
interface AuthState {
  /** Null when signed out. */
  user: CurrentUser | null;
  /** True while the initial "is there a session?" check is running. */
  initialising: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
  /** Re-fetches the user, e.g. after the profile is edited. */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

/**
 * Exported so tests can supply a user directly instead of stubbing the
 * `/users/me` request that AuthProvider makes on mount. Application code uses
 * {@link useAuth}.
 */
export const AuthContextForTests = AuthContext;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  const loadUser = useCallback(async (signal?: AbortSignal) => {
    const current = await authApi.currentUser(signal);
    setUser(current);
  }, []);

  // On first load: if a token survived a page refresh, find out whether it is
  // still any good. A token that has expired or was signed with a rotated
  // secret still *looks* valid in storage, so the only honest check is to use
  // it.
  useEffect(() => {
    if (!tokenStorage.has()) {
      setInitialising(false);
      return;
    }

    const controller = new AbortController();

    loadUser(controller.signal)
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        // 401 means the token is stale: drop it rather than leaving the app in
        // a half-signed-in state where every request fails.
        if (error instanceof ApiError && error.isUnauthenticated) {
          tokenStorage.clear();
        }
        setUser(null);
      })
      .finally(() => setInitialising(false));

    return () => controller.abort();
  }, [loadUser]);

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await authApi.login(request);
      tokenStorage.set(response.accessToken);
      // The login response carries the account but not the profile, and the
      // dashboard needs both — so this reads the full record rather than
      // storing a partial user that later code would have to special-case.
      await loadUser();
    },
    [loadUser],
  );

  const register = useCallback(
    async (request: RegisterRequest) => {
      await authApi.register(request);
      // Registration deliberately returns no token, so signing in is a second
      // call. Doing it here keeps that a backend detail rather than something
      // the form has to know about.
      await login({ email: request.email, password: request.password });
    },
    [login],
  );

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    if (tokenStorage.has()) {
      await loadUser();
    }
  }, [loadUser]);

  const value = useMemo<AuthState>(
    () => ({ user, initialising, login, register, logout, refresh }),
    [user, initialising, login, register, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * @throws if used outside the provider, which is a wiring bug and should be
 *         loud rather than silently returning undefined
 */
export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
