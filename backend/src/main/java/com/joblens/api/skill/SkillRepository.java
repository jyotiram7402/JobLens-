package com.joblens.api.skill;

import com.joblens.api.skill.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByNormalizedName(String normalizedName);

    /**
     * Loads several skills at once.
     *
     * <p>Used by find-or-create so that resolving a profile's twenty skills
     * costs one query rather than twenty.
     */
    List<Skill> findByNormalizedNameIn(Collection<String> normalizedNames);
}
