package com.joblens.api.user;

import com.joblens.api.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link User}.
 *
 * <p>Callers pass an already-normalized (lowercased, trimmed) email, which
 * {@code User.normalizeEmail} produces. The stored column is normalized too, so
 * the lookup is case-insensitive without a functional index.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
