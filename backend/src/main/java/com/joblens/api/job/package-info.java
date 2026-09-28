/**
 * Public job openings belonging to a company, and the search over them.
 *
 * <p>Implemented in roadmap step 5 (domain and CRUD) and step 6 (search and
 * filtering).
 *
 * <pre>
 * job/
 *   JobController.java        HTTP layer: parameters, validation, status codes
 *   JobService.java           business rules and the transaction boundary
 *   JobRepository.java        persistence, plus the search entry point
 *   JobSearchCriteria.java    every filter, in one value
 *   JobSpecifications.java    criteria -> SQL predicates
 *   JobSortParser.java        sort parameter -> Sort, via an allowlist
 *   domain/                   Job, EmploymentType, WorkMode
 *   dto/                      request and response records
 *   exception/                domain failures, mapped to HTTP by ErrorCode
 * </pre>
 *
 * <p>This module reads {@code CompanyRepository} directly -- the one place the
 * "modules talk through services" rule bends, because associating a job with a
 * company needs a managed entity and {@code CompanyService} only returns DTOs.
 * It reads companies; it never writes them.
 *
 * <p>Search is deliberately unremarkable: dynamic JPA predicates over
 * PostgreSQL, with filtering, sorting and paging all done by the database.
 * There is no search engine, and V1 does not need one.
 */
package com.joblens.api.job;
