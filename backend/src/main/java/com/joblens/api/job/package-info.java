/**
 * Public job openings belonging to a company, and the search over them.
 *
 * <p>Implemented in roadmap step 5. This package currently defines a
 * boundary only -- it holds no code by design.
 *
 * <p>Module layout, once populated:
 * <pre>
 * job/
 *   JobController.java   HTTP layer: routing, validation, DTO mapping
 *   JobService.java      business rules and transaction boundaries
 *   JobRepository.java   persistence
 *   domain/                  entities owned by this module
 *   dto/                     request and response records
 * </pre>
 */
package com.joblens.api.job;
