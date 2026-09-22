/**
 * Companies: the canonical record a scan resolves to, and the owner of company search.
 *
 * <p>Implemented in roadmap step 3. This package currently defines a
 * boundary only -- it holds no code by design.
 *
 * <p>Module layout, once populated:
 * <pre>
 * company/
 *   CompanyController.java   HTTP layer: routing, validation, DTO mapping
 *   CompanyService.java      business rules and transaction boundaries
 *   CompanyRepository.java   persistence
 *   domain/                  entities owned by this module
 *   dto/                     request and response records
 * </pre>
 */
package com.joblens.api.company;
