package com.joblens.api.skill.domain;

import com.joblens.api.common.domain.BaseEntity;
import com.joblens.api.common.text.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One skill in the shared vocabulary -- "Java", "Spring Boot", "PostgreSQL".
 *
 * <p>Referenced by both user profiles and jobs, which is the whole reason it
 * exists as a table. Matching compares skill <em>identity</em>, and two free-text
 * columns can only ever be compared by string equality and will drift apart.
 *
 * <p>Identity is {@code normalizedName}, produced by the same
 * {@link TextNormalizer} used for company names, job titles and profile
 * preferences. {@code "Spring Boot"}, {@code "spring boot"} and
 * {@code "SPRING  BOOT"} converge on one row. {@code name} is only for display,
 * and keeps whatever capitalisation the first person to use it typed.
 *
 * <p>Deliberately not an ontology: no categories, no parent/child, no synonyms,
 * no proficiency. A skill is a name and its normalized form.
 */
@Entity
@Table(name = "skills")
public class Skill extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 80, updatable = false)
    private String normalizedName;

    /** For JPA only. */
    protected Skill() {
    }

    private Skill(String name, String normalizedName) {
        this.name = name;
        this.normalizedName = normalizedName;
    }

    /**
     * @throws IllegalArgumentException if the text normalizes to nothing, which
     *                                  would leave a skill with no identity
     */
    public static Skill of(String name) {
        String normalized = TextNormalizer.normalize(name);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Skill name contains no letters or digits: " + name);
        }
        return new Skill(name.trim(), normalized);
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    @Override
    public String toString() {
        return name;
    }
}
