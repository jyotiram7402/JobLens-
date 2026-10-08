/**
 * Where the access token lives.
 *
 * <p>Deliberately tiny. Authentication is wired up in a later step; this exists
 * so the API client has one place to ask for a token, and so that changing the
 * storage strategy later touches one file instead of every request.
 *
 * <h2>The storage trade-off, stated</h2>
 *
 * <p>The token is held in memory and mirrored to `sessionStorage`, not
 * `localStorage`. In-memory alone would log the user out on every refresh;
 * `localStorage` survives longer but persists across browser sessions and is
 * readable by any script that gets injected into the page. `sessionStorage` is
 * the middle ground: it survives a refresh and dies with the tab.
 *
 * <p>None of these resist XSS. The only storage that does is an httpOnly
 * cookie, which would mean switching the backend from header authentication to
 * cookies and taking on CSRF protection with it -- a real decision with real
 * consequences, not something to do in passing. It is written down in
 * ARCHITECTURE.md as the thing to revisit.
 *
 * <p>Every access is wrapped: storage throws in private mode and when site data
 * is blocked, and a thrown exception here would break the whole application for
 * a user whose browser is merely strict.
 */
const STORAGE_KEY = 'joblens.accessToken';

let inMemoryToken: string | null = null;

export const tokenStorage = {
  get(): string | null {
    if (inMemoryToken) {
      return inMemoryToken;
    }
    try {
      inMemoryToken = window.sessionStorage.getItem(STORAGE_KEY);
      return inMemoryToken;
    } catch {
      return null;
    }
  },

  set(token: string): void {
    inMemoryToken = token;
    try {
      window.sessionStorage.setItem(STORAGE_KEY, token);
    } catch {
      // The token still works for this page; it just will not survive a reload.
    }
  },

  clear(): void {
    inMemoryToken = null;
    try {
      window.sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing to do: the in-memory copy is already gone.
    }
  },

  has(): boolean {
    return Boolean(tokenStorage.get());
  },
};
