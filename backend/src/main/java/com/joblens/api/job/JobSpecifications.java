package com.joblens.api.job;

import com.joblens.api.job.domain.Job;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the {@code WHERE} clause for a job search, one filter at a time.
 *
 * <p>The pattern is Spring Data's {@link Specification}: each filter is a small
 * function producing a predicate, and {@link #from(JobSearchCriteria)} combines
 * the ones that apply. The alternative -- a JPQL query with
 * {@code (:param IS NULL OR column = :param)} repeated ten times -- works, but
 * it becomes unreadable at this many filters and makes PostgreSQL plan a query
 * containing conditions the caller did not ask for.
 *
 * <p>The whole point is that filtering happens <em>in the database</em>. Nothing
 * here loads rows to inspect them in Java; the predicates become SQL, and
 * paging is applied by PostgreSQL.
 *
 * <h2>Boolean structure</h2>
 *
 * <pre>
 *   search                AND
 *   companyId             AND
 *   location              AND
 *   employmentType        AND
 *   workMode              AND
 *   experience overlap    AND
 *   active                AND
 *   postedAfter/Before
 *
 * where `search` is itself:  title LIKE ...  OR  description LIKE ...
 * </pre>
 *
 * <p>Filters are AND-ed because each one is the user narrowing the result; the
 * keyword is OR-ed across two columns because they are two places the same word
 * might appear.
 *
 * <h2>Case insensitivity</h2>
 *
 * <p>Both sides are lowered: {@code LOWER(column) LIKE LOWER(:pattern)}. Simple,
 * portable, and correct for {@code java}, {@code Java}, {@code JAVA} and
 * {@code jAvA} alike.
 *
 * <p>The cost is that {@code LOWER(column)} cannot use a plain B-tree index --
 * and neither can a leading-wildcard {@code LIKE '%java%'} regardless. That is
 * an accepted V1 trade-off: it is a sequential scan, which is the right shape
 * for a few thousand jobs and the wrong one for a few million. The documented
 * upgrade is a {@code pg_trgm} GIN index, which needs an extension and is not
 * justified yet.
 */
final class JobSpecifications {

    private JobSpecifications() {
    }

    /**
     * Combines every filter the criteria actually set.
     *
     * <p>Starting from an unrestricted specification and {@code and}-ing onto it
     * is what makes "unset means do not filter" fall out naturally.
     */
    static Specification<Job> from(JobSearchCriteria criteria) {
        List<Specification<Job>> specifications = new ArrayList<>();

        addIfPresent(specifications, keywordMatches(criteria.search()));
        addIfPresent(specifications, belongsToCompany(criteria.companyId()));
        addIfPresent(specifications, locationContains(criteria.location()));
        addIfPresent(specifications, equalTo("employmentType", criteria.employmentType()));
        addIfPresent(specifications, equalTo("workMode", criteria.workMode()));
        addIfPresent(specifications, acceptsExperience(criteria.experienceMin(), criteria.experienceMax()));
        addIfPresent(specifications, equalTo("active", criteria.active()));
        addIfPresent(specifications, postedAfter(criteria.postedAfter()));
        addIfPresent(specifications, postedBefore(criteria.postedBefore()));

        return specifications.stream().reduce(Specification::and).orElse(null);
    }

    private static void addIfPresent(List<Specification<Job>> target, Specification<Job> candidate) {
        if (candidate != null) {
            target.add(candidate);
        }
    }

    /**
     * The keyword, matched against the title OR the description.
     *
     * <p>Only those two columns. Location has its own filter, and matching a
     * keyword against the company name would make {@code search=google} return
     * every opening at Google rather than jobs about Google -- a different
     * question, answered by {@code companyId}.
     */
    private static Specification<Job> keywordMatches(String search) {
        if (isBlank(search)) {
            return null;
        }
        String pattern = containsPattern(search);

        return (root, query, builder) -> builder.or(
                like(builder, root.get("title"), pattern),
                like(builder, root.get("description"), pattern));
    }

    private static Specification<Job> belongsToCompany(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        // root.get("company").get("id") compares the foreign key column on jobs.
        // It does not join to companies, so filtering by company costs nothing
        // extra.
        return (root, query, builder) -> builder.equal(root.get("company").get("id"), companyId);
    }

    private static Specification<Job> locationContains(String location) {
        if (isBlank(location)) {
            return null;
        }
        String pattern = containsPattern(location);
        return (root, query, builder) -> like(builder, root.get("location"), pattern);
    }

    private static <T> Specification<Job> equalTo(String attribute, T value) {
        if (value == null) {
            return null;
        }
        return (root, query, builder) -> builder.equal(root.get(attribute), value);
    }

    /**
     * Whether the job's accepted experience range overlaps the range the
     * searcher described.
     *
     * <p>Both ranges may be open at either end, and the rule is deliberately
     * generous about it:
     *
     * <pre>
     *   job range    = [ experienceMin  ?? 0 ,  experienceMax  ?? infinity ]
     *   search range = [ experienceMin  ?? 0 ,  experienceMax  ?? infinity ]
     *
     *   overlap  iff  jobMin &lt;= searchMax  AND  jobMax &gt;= searchMin
     * </pre>
     *
     * <p>So {@code experienceMin=2} returns every job that will accept someone
     * with 2 years -- including one asking for 0-5 and one asking for 5+, since
     * the searcher may have more than 2. And {@code experienceMax=4} returns
     * every job reachable by someone with at most 4 years.
     *
     * <p><b>A null bound on the job is an open bound, never zero.</b> An
     * employer who did not state a range has not said "no experience"; they have
     * said nothing. Treating null as 0 would quietly drop those jobs out of any
     * search with {@code experienceMin} set, which is exactly the class of bug
     * where users conclude the search is broken without being able to say why.
     * Each predicate is therefore written as "unspecified OR within bound".
     */
    private static Specification<Job> acceptsExperience(Integer searchMin, Integer searchMax) {
        if (searchMin == null && searchMax == null) {
            return null;
        }

        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (searchMax != null) {
                predicates.add(builder.or(
                        builder.isNull(root.get("experienceMin")),
                        builder.lessThanOrEqualTo(root.get("experienceMin"), searchMax)));
            }
            if (searchMin != null) {
                predicates.add(builder.or(
                        builder.isNull(root.get("experienceMax")),
                        builder.greaterThanOrEqualTo(root.get("experienceMax"), searchMin)));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Inclusive, so a boundary timestamp is a match rather than a near miss. */
    private static Specification<Job> postedAfter(Instant postedAfter) {
        if (postedAfter == null) {
            return null;
        }
        return (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("postedAt"), postedAfter);
    }

    /** Inclusive. */
    private static Specification<Job> postedBefore(Instant postedBefore) {
        if (postedBefore == null) {
            return null;
        }
        return (root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("postedAt"), postedBefore);
    }

    private static Predicate like(jakarta.persistence.criteria.CriteriaBuilder builder,
                                  Expression<String> column, String pattern) {
        // The explicit escape character is what makes escapeLikeWildcards below
        // actually work: without it PostgreSQL has no way to know that a
        // backslash in the pattern is an escape rather than a literal.
        return builder.like(builder.lower(column), pattern, '\\');
    }

    /**
     * Builds a {@code %term%} pattern, with the user's own wildcards defanged.
     *
     * <p>This matters. {@code %} and {@code _} are wildcards in {@code LIKE}, so
     * a search for {@code 100%} would otherwise match everything, and a search
     * for {@code _} would match every job with at least one character -- which
     * is all of them. Worse, a pattern of many wildcards is a cheap way to make
     * PostgreSQL do a great deal of backtracking on every row.
     *
     * <p>The company search does not need this because its terms go through
     * normalization, which strips those characters entirely. Job search matches
     * raw title and description text, so it has to escape them explicitly.
     */
    private static String containsPattern(String term) {
        return "%" + escapeLikeWildcards(term.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    private static String escapeLikeWildcards(String term) {
        return term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
