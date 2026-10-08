import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import type { UpdateUserProfileRequest, UserProfile } from './types';

export const profileApi = {
  get: (signal?: AbortSignal) => api.get<UserProfile>(endpoints.users.myProfile, { signal }),

  /**
   * Replaces the profile.
   *
   * <p>PUT, and the backend means it: an omitted field is cleared, and leaving
   * a skill out is how it gets removed. The form therefore submits every field
   * every time, not only the ones that changed.
   */
  update: (request: UpdateUserProfileRequest) =>
    api.put<UserProfile>(endpoints.users.myProfile, request),
};
