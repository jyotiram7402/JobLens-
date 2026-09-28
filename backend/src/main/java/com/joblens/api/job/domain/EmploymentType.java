package com.joblens.api.job.domain;

/**
 * The contractual shape of an opening.
 *
 * <p>Stored as a string, not an ordinal, so adding or reordering values cannot
 * silently reassign existing rows. The database has a matching CHECK
 * constraint, which means adding a value here also needs a migration -- that is
 * intentional friction: the set is part of the API contract.
 */
public enum EmploymentType {

    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    TEMPORARY,

    /** For postings that genuinely do not fit the others. */
    OTHER
}
