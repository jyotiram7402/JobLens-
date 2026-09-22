/**
 * Companies and jobs a user follows, and the status of each application.
 *
 * <p>Implemented in roadmap step 10. This package currently defines a
 * boundary only -- it holds no code by design.
 *
 * <p>Module layout, once populated:
 * <pre>
 * tracking/
 *   TrackingController.java   HTTP layer: routing, validation, DTO mapping
 *   TrackingService.java      business rules and transaction boundaries
 *   TrackingRepository.java   persistence
 *   domain/                  entities owned by this module
 *   dto/                     request and response records
 * </pre>
 */
package com.joblens.api.tracking;
