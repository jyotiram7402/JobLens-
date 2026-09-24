package com.joblens.api.company;

import java.util.function.Predicate;

/**
 * Builds the URL-safe identifier a company is addressed by.
 *
 * <p>{@code "Tata Consultancy Services"} becomes
 * {@code "tata-consultancy-services"}.
 *
 * <p>The base is the normalized name with spaces turned into hyphens, so the
 * slug inherits every rule in {@link CompanyNameNormalizer} -- lowercase, no
 * accents, no punctuation -- and is therefore safe in a URL without escaping.
 *
 * <p>Company names are not unique, and two different companies may well share
 * one. When the base slug is taken, a numeric suffix is appended:
 * {@code acme}, then {@code acme-2}, then {@code acme-3}. Suffixes start at 2
 * because the first holder owns the unsuffixed slug.
 *
 * <p>The caller supplies the "is this taken?" test rather than this class
 * reaching for a repository, which keeps slug generation a pure function and
 * testable without a database.
 */
public final class SlugGenerator {

    /**
     * Matches the {@code slug VARCHAR(220)} column, leaving room for a suffix
     * beyond the 200-character name limit.
     */
    static final int MAX_SLUG_LENGTH = 220;

    /**
     * A bound on suffix attempts. Reaching it would mean thousands of companies
     * sharing one normalized name, which cannot happen while normalized names
     * are unique -- so hitting this is a bug, not a busy day, and it should fail
     * rather than loop forever.
     */
    private static final int MAX_ATTEMPTS = 1000;

    private SlugGenerator() {
    }

    /**
     * Generates a slug that {@code isTaken} reports as free.
     *
     * <p>This is a best effort, not a guarantee: between the check here and the
     * insert, another request can claim the same value. The unique index on
     * {@code companies.slug} is the actual guarantee; this only keeps the common
     * case tidy.
     *
     * @param name    the company name, raw and unnormalized
     * @param isTaken returns {@code true} when a candidate is already in use
     * @return a slug not currently in use
     * @throws IllegalArgumentException if the name normalizes to nothing
     * @throws IllegalStateException    if no free slug is found within the
     *                                  attempt limit
     */
    public static String generate(String name, Predicate<String> isTaken) {
        String base = toSlug(name);
        if (base.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot build a slug from a name with no alphanumeric characters: " + name);
        }

        if (!isTaken.test(base)) {
            return base;
        }

        for (int suffix = 2; suffix <= MAX_ATTEMPTS; suffix++) {
            String candidate = withSuffix(base, suffix);
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Exhausted %d slug candidates for '%s'".formatted(MAX_ATTEMPTS, name));
    }

    /**
     * The unsuffixed slug for a name, with no uniqueness check.
     */
    public static String toSlug(String name) {
        String normalized = CompanyNameNormalizer.normalize(name);
        String slug = normalized.replace(' ', '-');
        return slug.length() > MAX_SLUG_LENGTH ? trimToWholeWord(slug, MAX_SLUG_LENGTH) : slug;
    }

    /**
     * Appends {@code -N}, shortening the base first if the result would not fit
     * in the column. A truncated slug is acceptable; an insert that fails
     * because the value is one character too long is not.
     */
    private static String withSuffix(String base, int suffix) {
        String tail = "-" + suffix;
        int room = MAX_SLUG_LENGTH - tail.length();
        String head = base.length() > room ? trimToWholeWord(base, room) : base;
        return head + tail;
    }

    /**
     * Cuts at the last hyphen within the limit, so a slug does not end in half a
     * word, and never leaves a trailing hyphen.
     */
    private static String trimToWholeWord(String slug, int limit) {
        String cut = slug.substring(0, limit);
        int lastHyphen = cut.lastIndexOf('-');
        if (lastHyphen > 0) {
            cut = cut.substring(0, lastHyphen);
        }
        return cut.endsWith("-") ? cut.substring(0, cut.length() - 1) : cut;
    }
}
