/**
 * The career profile. Mirrors `UserProfileResponse` and
 * `UpdateUserProfileRequest`.
 */
import type { User } from '../auth/types';

/** `ANY` means no preference stated, and matching skips the criterion. */
export type RemotePreference = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'ANY';

export interface UserProfile {
  headline: string | null;
  summary: string | null;
  yearsOfExperience: number | null;
  /** The field is `currentRole` on the wire. */
  currentRole: string | null;
  remotePreference: RemotePreference;
  preferredRoles: string[];
  preferredLocations: string[];
  /** Display names from the shared skill vocabulary. */
  skills: string[];
}

/** `GET /users/me` returns the account and profile together. */
export interface CurrentUser extends User {
  profile: UserProfile;
}

/**
 * `PUT /users/me/profile`. Full replacement: an omitted field is cleared, and
 * leaving a skill out is how it is removed.
 *
 * <p>There is no `userId` — the profile written is always the token's.
 */
export interface UpdateUserProfileRequest {
  headline?: string | null;
  summary?: string | null;
  /** 0-60. */
  yearsOfExperience?: number | null;
  currentRole?: string | null;
  remotePreference?: RemotePreference | null;
  /** At most 20. */
  preferredRoles?: string[];
  /** At most 20. */
  preferredLocations?: string[];
  /** At most 50. Deduplicated server-side, so "Java" and "java" are one. */
  skills?: string[];
}
