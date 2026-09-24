package com.joblens.api.company;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugGeneratorTest {

    @Test
    void buildsSlugFromName() {
        assertThat(SlugGenerator.toSlug("Tata Consultancy Services"))
                .isEqualTo("tata-consultancy-services");
    }

    @Test
    void inheritsNormalizationSoCasingAndPunctuationDoNotLeakIntoUrls() {
        assertThat(SlugGenerator.toSlug("  NESTLE  S.A. ")).isEqualTo("nestle-s-a");
    }

    @Test
    void usesBaseSlugWhenFree() {
        assertThat(SlugGenerator.generate("Acme", slug -> false)).isEqualTo("acme");
    }

    @Test
    void suffixesFromTwoWhenBaseIsTaken() {
        Set<String> taken = Set.of("acme");
        assertThat(SlugGenerator.generate("Acme", taken::contains)).isEqualTo("acme-2");
    }

    @Test
    void keepsCountingPastTheFirstCollision() {
        Set<String> taken = Set.of("acme", "acme-2", "acme-3");
        assertThat(SlugGenerator.generate("Acme", taken::contains)).isEqualTo("acme-4");
    }

    @Test
    void keepsLongSlugsWithinTheColumnLimit() {
        String longName = "Very Long Company Name ".repeat(20);

        String slug = SlugGenerator.toSlug(longName);

        assertThat(slug).hasSizeLessThanOrEqualTo(SlugGenerator.MAX_SLUG_LENGTH);
        assertThat(slug).doesNotEndWith("-");
    }

    @Test
    void keepsSuffixedLongSlugsWithinTheColumnLimit() {
        String longName = "Very Long Company Name ".repeat(20);
        String base = SlugGenerator.toSlug(longName);

        String slug = SlugGenerator.generate(longName, candidate -> candidate.equals(base));

        assertThat(slug).hasSizeLessThanOrEqualTo(SlugGenerator.MAX_SLUG_LENGTH);
        assertThat(slug).endsWith("-2");
    }

    @Test
    void rejectsNameWithNothingToSlugify() {
        assertThatThrownBy(() -> SlugGenerator.generate("!!!", slug -> false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
