/**
 * Companies a user follows.
 *
 * <p>Implemented in roadmap step 10. Job tracking and application status are
 * not built yet; this module currently covers companies only.
 *
 * <pre>
 * tracking/
 *   TrackingController.java          POST/DELETE/GET /companies/{id}/track,
 *                                    GET /users/me/tracked-companies
 *   TrackingService.java             idempotent track/untrack, status, list
 *   TrackedCompanyRepository.java    persistence
 *   domain/TrackedCompany.java       user -> company, with when it started
 *   dto/                             response records
 * </pre>
 *
 * <p>Three rules this module exists to keep:
 *
 * <ol>
 *   <li><b>One user, one company, one row</b> -- enforced by a unique
 *       constraint in the database, not only by a check in code, because a
 *       check-then-insert is not atomic.</li>
 *   <li><b>The user always comes from the token.</b> No endpoint accepts a
 *       user id, so no request can read or change another user's tracking.</li>
 *   <li><b>Tracking and untracking are idempotent.</b> Asking for a state that
 *       already holds is a success, not a conflict.</li>
 * </ol>
 */
package com.joblens.api.tracking;
