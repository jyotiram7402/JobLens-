package com.joblens.api.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reduces free text to a canonical form for comparison, deduplication and
 * search.
 *
 * <p>Deterministic and rule-based. The same input always produces the same
 * output, on any machine, with no model and no network call -- which is what
 * lets the result be stored in a unique index.
 *
 * <h2>The rules, in order</h2>
 *
 * <ol>
 *   <li><b>Unicode NFKC</b> -- collapses compatibility forms so a full-width or
 *       ligature character becomes its plain equivalent.</li>
 *   <li><b>Strip diacritics</b> -- decompose to NFD and drop combining marks, so
 *       {@code Nestlé} and {@code Nestle} match. OCR frequently loses or invents
 *       accents, so this matters more here than in a normal CRUD app.</li>
 *   <li><b>Lowercase</b> with {@link Locale#ROOT}, not the default locale -- a
 *       Turkish JVM lowercases {@code I} to a dotless {@code ı}, which would
 *       make normalization depend on where the server runs.</li>
 *   <li><b>Replace everything that is not a letter, digit or space with a
 *       space.</b> Punctuation carries no identity: {@code T.C.S.} and
 *       {@code TCS} are the same thing, as are {@code Node.js} and
 *       {@code Node js}.</li>
 *   <li><b>Collapse whitespace</b> to single spaces and trim.</li>
 * </ol>
 *
 * <p>A useful side effect: the output can never contain SQL {@code LIKE}
 * wildcards, because {@code %} and {@code _} are removed before the value
 * reaches a query.
 *
 * <p>Lives in {@code common} because more than one module needs it -- companies
 * deduplicate names with it, profiles deduplicate skills, roles and locations
 * with it, and step 7 will match the two against each other. Sharing the rules
 * is the point: a skill normalized one way here and another way there would not
 * join.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\p{M}+");

    /**
     * UNICODE_CHARACTER_CLASS matters here. Without it {@code \p{Alnum}} means
     * ASCII only, so text in Devanagari, Cyrillic or Japanese would have every
     * character stripped and normalize to an empty string.
     */
    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^\p{Alnum} ]+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern WHITESPACE = Pattern.compile("\s+");

    private TextNormalizer() {
    }

    /**
     * @param text raw input, may be {@code null}
     * @return the canonical form, or an empty string when the input is
     *         {@code null}, blank, or made up entirely of characters the rules
     *         discard (for example {@code "!!!"})
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = Normalizer.normalize(text, Normalizer.Form.NFKC);

        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = COMBINING_MARKS.matcher(result).replaceAll("");

        result = result.toLowerCase(Locale.ROOT);

        // Replaced with a space rather than removed, so "Acme/Widgets" becomes
        // two words instead of one nonsense token.
        result = NON_ALPHANUMERIC.matcher(result).replaceAll(" ");

        return WHITESPACE.matcher(result).replaceAll(" ").trim();
    }
}
