package com.joblens.api.job;

import com.joblens.api.common.exception.InvalidRequestException;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Turns a {@code sort=field,direction} parameter into a {@link Sort}, using an
 * allowlist.
 *
 * <p>Spring Data can bind a {@code Pageable} straight from request parameters,
 * which would have saved this class -- and would also let a caller order by any
 * mapped property. That is worth avoiding for two reasons: sorting by an
 * unindexed column on a large table is a cheap way to make the database work
 * very hard, and a misspelled property name becomes a
 * {@code PropertyReferenceException} and a 500 rather than a 400.
 *
 * <p>So the accepted fields are enumerated. Three of them, because three is
 * what a job list needs; the rest can be added when something asks for them.
 *
 * <p>Sorting is <b>stable</b>: the requested field is always followed by
 * {@code id}. Without a tiebreaker, two jobs posted in the same second can swap
 * places between page 1 and page 2, so the same row appears twice and another
 * is never seen. That bug is invisible in testing with distinct data and
 * obvious in production.
 */
final class JobSortParser {

    /**
     * Sort keys a client may use, mapped to entity properties.
     *
     * <p>Keys are the public API; values are internal names. Keeping them
     * separate means renaming a field does not break every saved search.
     */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "postedAt", "postedAt",
            "createdAt", "createdAt",
            "title", "title");

    /**
     * Newest first. For job discovery, a stale opening is worse than a less
     * relevant one, and we have no relevance score to sort by yet.
     */
    static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("postedAt"), Sort.Order.asc("id"));

    private JobSortParser() {
    }

    /**
     * @param sort {@code "field"} or {@code "field,asc"} / {@code "field,desc"};
     *             blank or null yields {@link #DEFAULT_SORT}
     * @throws InvalidRequestException if the field is not sortable or the
     *                                 direction is not recognised
     */
    static Sort parse(String sort) {
        if (!StringUtils.hasText(sort)) {
            return DEFAULT_SORT;
        }

        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();

        String property = SORTABLE_FIELDS.get(field);
        if (property == null) {
            throw new InvalidRequestException(
                    "sort field '%s' is not supported. Allowed: %s"
                            .formatted(field, String.join(", ", SORTABLE_FIELDS.keySet())));
        }

        Sort.Direction direction = parts.length > 1 ? parseDirection(parts[1].trim()) : defaultDirectionFor(field);

        return Sort.by(new Sort.Order(direction, property), Sort.Order.asc("id"));
    }

    private static Sort.Direction parseDirection(String direction) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidRequestException(
                    "sort direction '%s' is not supported. Allowed: asc, desc".formatted(direction));
        };
    }

    /**
     * A direction the caller probably meant. Dates newest-first, text A-Z --
     * asking for {@code sort=title} and getting Z-A would be surprising, and so
     * would {@code sort=postedAt} returning the oldest jobs.
     */
    private static Sort.Direction defaultDirectionFor(String field) {
        return "title".equals(field) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}
