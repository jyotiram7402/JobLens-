/**
 * Companies: the canonical record a scan resolves to, and the owner of company
 * search.
 *
 * <p>Implemented in roadmap step 3. This is the first populated domain module
 * and the template the others follow.
 *
 * <pre>
 * company/
 *   CompanyController.java       HTTP layer: routing, validation, status codes
 *   CompanyService.java          business rules and the transaction boundary
 *   CompanyRepository.java       persistence
 *   CompanyNameNormalizer.java   deterministic name canonicalisation
 *   SlugGenerator.java           URL identifier, with collision handling
 *   domain/Company.java          entity -- never leaves the service layer
 *   dto/                         request and response records
 *   exception/                   domain failures, mapped to HTTP by ErrorCode
 * </pre>
 *
 * <p>Other modules use {@code CompanyService}. Nothing outside this package
 * touches {@code CompanyRepository} or the {@code Company} entity -- that is the
 * boundary a service extraction would follow.
 */
package com.joblens.api.company;
