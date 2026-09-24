package com.joblens.api.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normalization rules are the contract that duplicate detection and search
 * both depend on, so they are pinned here rather than left implicit.
 */
class CompanyNameNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Tata Consultancy Services",
            "TATA CONSULTANCY SERVICES",
            "tata consultancy services",
            "  Tata   Consultancy  Services  ",
            "Tata Consultancy Services.",
            "Tata, Consultancy & Services"
    })
    void normalizesEveryWritingOfTheSameNameIdentically(String input) {
        assertThat(CompanyNameNormalizer.normalize(input))
                .isEqualTo("tata consultancy services");
    }

    @ParameterizedTest
    @CsvSource({
            "'Nestlé S.A.',        'nestle s a'",
            "'Café Group',         'cafe group'",
            "'Zürich Insurance',   'zurich insurance'",
            "'T.C.S.',             'tcs'",
            "'Acme/Widgets',       'acme widgets'",
            "'Siemens AG',         'siemens ag'"
    })
    void appliesTheDocumentedRules(String input, String expected) {
        assertThat(CompanyNameNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!", "---", "   ", "@#$%"})
    void returnsEmptyWhenNothingMeaningfulRemains(String input) {
        assertThat(CompanyNameNormalizer.normalize(input)).isEmpty();
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(CompanyNameNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void keepsNonLatinScripts() {
        // Guards the UNICODE_CHARACTER_CLASS flag: without it, \p{Alnum} is
        // ASCII-only and this would normalize to an empty string.
        assertThat(CompanyNameNormalizer.normalize("Мосэнерго")).isEqualTo("мосэнерго");
    }

    @Test
    void doesNotStripLegalSuffixes() {
        // Documented non-goal: merging these is entity resolution, not
        // normalization, and belongs to the AI company-resolution step.
        assertThat(CompanyNameNormalizer.normalize("Acme Ltd"))
                .isNotEqualTo(CompanyNameNormalizer.normalize("Acme"));
    }

    @Test
    void removesLikeWildcardsSoASearchTermCannotCarryThem() {
        assertThat(CompanyNameNormalizer.normalize("100% pure_gold"))
                .isEqualTo("100 pure gold");
    }
}
