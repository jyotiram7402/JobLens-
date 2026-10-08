/**
 * Companies. Mirrors `CompanyResponse` and `CompanySummary`.
 */

/** The full record, from `/companies/{id}`. */
export interface Company {
  id: string;
  /** Stable URL identifier; never changes, even when the company is renamed. */
  slug: string;
  name: string;
  description: string | null;
  websiteUrl: string | null;
  careersUrl: string | null;
  logoUrl: string | null;
  industry: string | null;
  location: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * A search result row. Smaller on purpose — no description, which on a page of
 * twenty would be most of the payload for text no list shows.
 */
export interface CompanySummary {
  id: string;
  slug: string;
  name: string;
  industry: string | null;
  location: string | null;
  logoUrl: string | null;
}

export interface CompanySearchParams {
  search?: string;
  page?: number;
  size?: number;
}
