/**
 * Accounts, credentials and the profile a job is matched against.
 *
 * <p>Implemented in roadmap step 4. This package currently defines a
 * boundary only -- it holds no code by design.
 *
 * <p>Module layout, once populated:
 * <pre>
 * user/
 *   UserController.java   HTTP layer: routing, validation, DTO mapping
 *   UserService.java      business rules and transaction boundaries
 *   UserRepository.java   persistence
 *   domain/                  entities owned by this module
 *   dto/                     request and response records
 * </pre>
 */
package com.joblens.api.user;
