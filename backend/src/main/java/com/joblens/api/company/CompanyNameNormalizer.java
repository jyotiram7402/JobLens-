package com.joblens.api.company;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reduces a company name to a canonical form used for duplicate detection and
 * search.
 *
 * <p>Deterministic and rule-based on purpose. The same input always produces the
 * same output, on any machine, with no model and no network call -- which is
 * what lets the result be stored in a unique index. AI-assisted company
 * resolution is a later step and a different problem.
 *
 * <h2>The rules, in order</h2>
 *
 * <ol>
 *   <li><b>Unicode NFKC</b> -- collapses compatibility forms so that a
 *       full-width or ligature character becomes its plain equivalent.</li>
 *   <li><b>Strip diacritics</b> -- decompose to NFD and drop combining marks,
 *       so {@code Nestlé} and {@code Nestle} match. OCR frequently loses or
 *       invents accents, so this matters more here than in a normal CRUD app.</li>
 *   <li><b>Lowercase</b> using {@link Locale#ROOT}, not the default locale --
 *       a Turkish JVM lowercases {@code I} to a dotless {@code ı}, which would
 *       make normalization depend on where the server runs.</li>
 *   <li><b>Drop everything that is not a letter, digit or space.</b> Punctuation
 *       carries no identity: {@code T.C.S.} and {@code TCS} are the same
 *       company, as are {@code Tata & Sons} and {@code Tata Sons}.</li>
 *   <li><b>Collapse whitespace</b> to single spaces and trim.</li>
 * </ol>
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
 *
 * <p>A useful side effect: because the rules remove every character that is not
 * alphanumeric or a space, a normalized search term cannot contain SQL
 * {@code LIKE} wildcards. {@code %} and {@code _} are gone before the value ever
 * reaches a query.
 */
public final class CompanyNameNormalizer {

    private static final Pattern COMBINING_MARKS =
            Pattern.compile("\\p{M}+");

    /**
     * UNICODE_CHARACTER_CLASS matters here. Without it {@code \p{Alnum}} means
     * ASCII only, so a company written in Devanagari, Cyrillic or Japanese would
     * have every character stripped and normalize to an empty string.
     */
    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^\\p{Alnum} ]+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+");

    private CompanyNameNormalizer() {
    }

    /**
     * Normalizes a company name.
     *
     * @param name raw input, may be {@code null}
     * @return the canonical form, or an empty string when the input is
     *         {@code null}, blank, or made up entirely of characters the rules
     *         discard (for example {@code "!!!"})
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        String result = Normalizer.normalize(name, Normalizer.Form.NFKC);

        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = COMBINING_MARKS.matcher(result).replaceAll("");

        result = result.toLowerCase(Locale.ROOT);

        // Replaced with a space rather than removed, so "Acme/Widgets" becomes
        // two words instead of one nonsense token.
        result = NON_ALPHANUMERIC.matcher(result).replaceAll(" ");

        return WHITESPACE.matcher(result).replaceAll(" ").trim();
    }
}
