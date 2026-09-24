package com.joblens.api.user;

import com.joblens.api.user.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link UserProfile}.
 *
 * <p>No {@code @EntityGraph} over the three collections, deliberately. Fetch
 * joining all of them in one query produces a cartesian product -- 50 skills,
 * 20 roles and 20 locations would come back as 20,000 rows for Hibernate to
 * deduplicate in memory. Leaving them lazy costs three extra small queries
 * inside the same transaction, which is the cheaper end of that trade.
 *
 * <p>This only works because profiles are mapped to DTOs inside the
 * transactional service method. Serialising the entity outside it would hit a
 * lazy-initialisation failure instead, which is the usual way this mistake
 * announces itself.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);
}
