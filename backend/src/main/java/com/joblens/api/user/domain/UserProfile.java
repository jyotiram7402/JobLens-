package com.joblens.api.user.domain;

import com.joblens.api.common.domain.BaseEntity;
import com.joblens.api.common.domain.NormalizedText;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A user's career profile: what they do, what they want, and what they know.
 *
 * <p>Separate from {@link User} because the two change for different reasons and
 * at different rates. An account is identity and credentials; a profile is
 * career data that step 7 will match jobs against. Putting the career fields on
 * {@code users} would mean loading a password hash every time the matcher wants
 * a skill list.
 *
 * <p>Created together with the account, so every user always has one and no
 * endpoint has to handle a missing profile.
 *
 * <p>Skills, preferred roles and preferred locations are <b>element
 * collections</b>, not entities. Each row is a value with no identity or
 * lifecycle of its own: it exists because the profile says so and disappears
 * with it. That gives cascade and orphan removal for free, and keeps three
 * tables from needing three entity classes, three repositories and three sets of
 * plumbing.
 *
 * <p>Each value carries a normalized form
 * ({@link NormalizedText}), produced by the same rules the company domain uses.
 * That is what makes {@code Set} deduplication work -- adding {@code "java"} to
 * a profile that already lists {@code "Java"} is not a second skill -- and it is
 * what step 7 will join on.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

    /**
     * Lazy because most reads of a profile do not need the account behind it,
     * and eager would drag a password hash into memory for no reason.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @Column(name = "headline", length = 200)
    private String headline;

    @Column(name = "summary", length = 2000)
    private String summary;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    /** Column named to avoid the SQL reserved word {@code CURRENT_ROLE}. */
    @Column(name = "current_job_title", length = 150)
    private String currentJobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_preference", nullable = false, length = 20)
    private RemotePreference remotePreference = RemotePreference.ANY;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "profile_id"))
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 80)),
            @AttributeOverride(name = "normalizedValue", column = @Column(name = "normalized_name", nullable = false, length = 80))
    })
    private Set<NormalizedText> skills = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_preferred_roles", joinColumns = @JoinColumn(name = "profile_id"))
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "title", nullable = false, length = 150)),
            @AttributeOverride(name = "normalizedValue", column = @Column(name = "normalized_title", nullable = false, length = 150))
    })
    private Set<NormalizedText> preferredRoles = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_preferred_locations", joinColumns = @JoinColumn(name = "profile_id"))
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 150)),
            @AttributeOverride(name = "normalizedValue", column = @Column(name = "normalized_name", nullable = false, length = 150))
    })
    private Set<NormalizedText> preferredLocations = new LinkedHashSet<>();

    /** For JPA only. */
    protected UserProfile() {
    }

    private UserProfile(User user) {
        this.user = user;
        this.remotePreference = RemotePreference.ANY;
    }

    /**
     * An empty profile for a newly registered account, so that fetching a
     * profile never has to cope with one not existing.
     */
    public static UserProfile emptyFor(User user) {
        return new UserProfile(user);
    }

    /**
     * Replaces every editable field. PUT semantics: anything omitted is
     * cleared, not preserved.
     *
     * <p>The collections are replaced rather than merged for the same reason.
     * Removing a skill is then just leaving it out, with no separate delete
     * endpoint to build.
     */
    public void update(String headline, String summary, Integer yearsOfExperience,
                       String currentJobTitle, RemotePreference remotePreference,
                       Collection<String> skills, Collection<String> preferredRoles,
                       Collection<String> preferredLocations) {
        this.headline = headline;
        this.summary = summary;
        this.yearsOfExperience = yearsOfExperience;
        this.currentJobTitle = currentJobTitle;
        this.remotePreference = remotePreference == null ? RemotePreference.ANY : remotePreference;

        replace(this.skills, skills);
        replace(this.preferredRoles, preferredRoles);
        replace(this.preferredLocations, preferredLocations);
    }

    /**
     * Mutates the existing collection rather than assigning a new one. Hibernate
     * tracks the instance it handed us; replacing the field with a fresh
     * {@code Set} detaches that tracking and throws on flush.
     */
    private static void replace(Set<NormalizedText> target, Collection<String> values) {
        target.clear();
        if (values != null) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(NormalizedText::of)
                    .forEach(target::add);
        }
    }

    public User getUser() {
        return user;
    }

    public String getHeadline() {
        return headline;
    }

    public String getSummary() {
        return summary;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public String getCurrentJobTitle() {
        return currentJobTitle;
    }

    public RemotePreference getRemotePreference() {
        return remotePreference;
    }

    public List<String> getSkills() {
        return valuesOf(skills);
    }

    public List<String> getPreferredRoles() {
        return valuesOf(preferredRoles);
    }

    public List<String> getPreferredLocations() {
        return valuesOf(preferredLocations);
    }

    private static List<String> valuesOf(Set<NormalizedText> entries) {
        return entries.stream().map(NormalizedText::getValue).toList();
    }
}
