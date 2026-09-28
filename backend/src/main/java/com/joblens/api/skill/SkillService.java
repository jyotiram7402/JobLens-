package com.joblens.api.skill;

import com.joblens.api.common.text.TextNormalizer;
import com.joblens.api.skill.domain.Skill;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns skill names into shared {@link Skill} rows, creating any that are new.
 *
 * <p>The vocabulary is open: a user or an importer may name a skill nobody has
 * used before, and the system should record it rather than reject it. A curated
 * list would need curating, and getting that wrong means a user cannot describe
 * what they actually do.
 *
 * <p>The cost is that near-duplicates can appear -- "NodeJS" and "Node.js"
 * normalize differently. That is accepted for V1 and is why normalization is
 * shared and documented; a synonym table is a later decision with evidence
 * behind it.
 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * Resolves names to skills, creating the ones that do not exist yet.
     *
     * <p>Two queries regardless of how many names are supplied: one to find the
     * existing rows, one flush for the new ones. Resolving one at a time would
     * be a query per skill on every profile save.
     *
     * <p>Names that normalize to nothing -- punctuation, emoji -- are dropped
     * rather than rejected, because the request DTO has already enforced
     * non-blank and the caller should not get a 500 for a stray character.
     */
    @Transactional
    public Set<Skill> resolveAll(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }

        // LinkedHashMap so the caller's ordering survives, and so duplicates
        // that differ only in capitalisation collapse before anything is saved.
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (String name : names) {
            String normalized = TextNormalizer.normalize(name);
            if (!normalized.isEmpty()) {
                byNormalized.putIfAbsent(normalized, name.trim());
            }
        }
        if (byNormalized.isEmpty()) {
            return Set.of();
        }

        Map<String, Skill> existing = skillRepository
                .findByNormalizedNameIn(byNormalized.keySet()).stream()
                .collect(Collectors.toMap(Skill::getNormalizedName, Function.identity()));

        Set<Skill> resolved = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : byNormalized.entrySet()) {
            Skill skill = existing.get(entry.getKey());
            resolved.add(skill != null ? skill : create(entry.getKey(), entry.getValue()));
        }
        return resolved;
    }

    /**
     * Creates a skill, tolerating the race with another request creating the
     * same one.
     *
     * <p>The lookup above and this insert are not atomic. The unique index on
     * {@code normalized_name} settles it, and the loser simply re-reads the row
     * the winner created -- a conflict here is not an error, it is two people
     * typing "Kubernetes" at once.
     */
    private Skill create(String normalizedName, String displayName) {
        try {
            return skillRepository.saveAndFlush(Skill.of(displayName));
        } catch (DataIntegrityViolationException ex) {
            return skillRepository.findByNormalizedName(normalizedName)
                    .orElseThrow(() -> ex);
        }
    }
}
