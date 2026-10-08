import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import type { AuthResponse, LoginRequest, RegisterRequest, RegistrationResponse } from './types';
import type { CurrentUser } from '../profile/types';

/**
 * Authentication calls.
 *
 * <p>One module per feature, so a component never reaches for the HTTP client
 * itself. That keeps the call sites readable and means a change to an endpoint
 * is one edit.
 */
export const authApi = {
  /** Creates an account. Returns the user and **no token** — log in next. */
  register: (request: RegisterRequest) =>
    api.post<RegistrationResponse>(endpoints.auth.register, request),

  /** Exchanges credentials for an access token. */
  login: (request: LoginRequest) => api.post<AuthResponse>(endpoints.auth.login, request),

  /** The signed-in user and their career profile, in one request. */
  currentUser: (signal?: AbortSignal) =>
    api.get<CurrentUser>(endpoints.users.me, { signal }),
};
