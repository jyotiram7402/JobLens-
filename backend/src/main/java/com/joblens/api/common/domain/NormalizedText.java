package com.joblens.api.common.domain;

import com.joblens.api.common.text.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * A short piece of user-supplied text stored alongside its normalized form.
 *
 * <p>Used for value collections such as a profile's skills, preferred roles and
 * preferred locations. The original is what gets shown back to the user; the
 * normalized form is what uniqueness and, later, job matching compare.
 *
 * <p>Equality is on the normalized value alone, which is what makes a
 * {@code Set} of these deduplicate correctly: adding {@code "java"} to a
 * collection that already contains {@code "Java"} changes nothing.
 *
 * <p>Column names are supplied per collection via {@code @AttributeOverride},
 * so one embeddable serves three differently named tables.
 */
@Embeddable
public class NormalizedText {

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "normalized_value", nullable = false)
    private String normalizedValue;

    /** For JPA only. */
    protected NormalizedText() {
    }

    private NormalizedText(String value, String normalizedValue) {
        this.value = value;
        this.normalizedValue = normalizedValue;
    }

    /**
     * @throws IllegalArgumentException if the text normalizes to nothing, which
     *                                  would leave a row with no comparable form
     */
    public static NormalizedText of(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Value contains no letters or digits: " + text);
        }
        return new NormalizedText(text.trim(), normalized);
    }

    public String getValue() {
        return value;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NormalizedText that
                && Objects.equals(normalizedValue, that.normalizedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(normalizedValue);
    }

    @Override
    public String toString() {
        return value;
    }
}
