/**
 * Image capture, upload and the resolution of a scan to a verified company.
 *
 * <p>Implemented in roadmap step 11. This package currently defines a
 * boundary only -- it holds no code by design.
 *
 * <p>Module layout, once populated:
 * <pre>
 * scan/
 *   ScanController.java   HTTP layer: routing, validation, DTO mapping
 *   ScanService.java      business rules and transaction boundaries
 *   ScanRepository.java   persistence
 *   domain/                  entities owned by this module
 *   dto/                     request and response records
 * </pre>
 */
package com.joblens.api.scan;
