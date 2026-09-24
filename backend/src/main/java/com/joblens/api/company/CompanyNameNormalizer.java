package com.joblens.api.company;

import com.joblens.api.common.text.TextNormalizer;

/**
 * Reduces a company name to the canonical form used for duplicate detection and
 * search.
 *
 * <p>The mechanics live in {@link TextNormalizer}, shared with the user module
 * so that skills, roles and locations are canonicalised by exactly the same
 * rules. This type exists to record what those rules mean *for company names*,
 * and to give the company domain a name to depend on if the two ever need to
 * diverge.
 *
 * <p>Example: {@code "  Tata   Consultancy  Services "},
 * {@code "TATA CONSULTANCY SERVICES"} and {@code "Tata Consultancy Services."}
 * all become {@code "tata consultancy services"}.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It does not strip legal suffixes. {@code "Acme Ltd"} and {@code "Acme"}
 * normalize differently and stay two records. Removing {@code Ltd}, {@code Inc},
 * {@code GmbH} and the rest is entity resolution, not normalization: it needs
 * judgement about which suffixes are noise in which jurisdiction, it merges
 * companies that genuinely are distinct legal entities, and being wrong is
 * expensive because a unique index makes the merge permanent. That belongs to
 * the AI company-resolution step, where a confidence score and human review are
 * available.
 *
 * <p>It does not translate, transliterate non-Latin scripts, or expand
 * abbreviations. Same reasoning.
 */
public final class CompanyNameNormalizer {

    private CompanyNameNormalizer() {
    }

    /**
     * @param name raw input, may be {@code null}
     * @return the canonical form, or an empty string when nothing meaningful
     *         remains
     */
    public static String normalize(String name) {
        return TextNormalizer.normalize(name);
    }
}
