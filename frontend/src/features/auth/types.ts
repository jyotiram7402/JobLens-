/**
 * Authentication contract. Mirrors the backend's `user` DTOs.
 */

/** The safe public view of an account: no role, no active flag, no hash. */
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
}

export interface RegisterRequest {
  email: string;
  /** 10-72 characters. The upper bound is bcrypt's; longer is rejected. */
  password: string;
  firstName: string;
  lastName: string;
}

/** Registration deliberately returns no token — call login next. */
export interface RegistrationResponse {
  user: User;
  message: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  /** Always `Bearer`. */
  tokenType: string;
  /** Lifetime in seconds, so a client can act before a 401 arrives. */
  expiresIn: number;
  user: User;
}
