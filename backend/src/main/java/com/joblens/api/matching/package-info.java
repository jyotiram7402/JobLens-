/**
 * Scores a job against a user profile and explains why it scored that way.
 *
 * <p>Implemented in roadmap step 7. This package currently defines a
 * boundary only -- it holds no code by design.
 *
 * <p>Module layout, once populated:
 * <pre>
 * matching/
 *   MatchingController.java   HTTP layer: routing, validation, DTO mapping
 *   MatchingService.java      business rules and transaction boundaries
 *   MatchingRepository.java   persistence
 *   domain/                  entities owned by this module
 *   dto/                     request and response records
 * </pre>
 */
package com.joblens.api.matching;
