/**
 * Company tracking. Mirrors `TrackingStatusResponse` and
 * `TrackedCompanyResponse` on the backend.
 */

/**
 * Whether the signed-in user tracks a company.
 *
 * <p>Returned by both GET and POST on `/companies/{id}/track`, so tracking a
 * company hands back the new state directly and the UI needs no second request.
 */
export interface TrackingStatus {
  companyId: string;
  tracked: boolean;
  /** ISO-8601. Null when not tracked. */
  trackedAt: string | null;
}

/**
 * A row of the tracked-companies list. A card's worth of company information
 * plus when tracking started; deliberately not the full company record.
 */
export interface TrackedCompany {
  companyId: string;
  name: string;
  slug: string;
  logoUrl: string | null;
  industry: string | null;
  location: string | null;
  websiteUrl: string | null;
  careersUrl: string | null;
  trackedAt: string;
}
