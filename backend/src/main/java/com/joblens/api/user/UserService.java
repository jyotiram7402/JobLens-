package com.joblens.api.user;

import com.joblens.api.user.domain.User;
import com.joblens.api.user.domain.UserProfile;
import com.joblens.api.user.dto.CurrentUserResponse;
import com.joblens.api.user.dto.UpdateUserProfileRequest;
import com.joblens.api.user.dto.UserProfileResponse;
import com.joblens.api.user.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reading and updating the signed-in user's account and career profile.
 *
 * <p>Every method takes the user id as an argument, and every caller gets that
 * id from the security context -- never from a request body or a path variable.
 * That is the whole defence against one user editing another's profile, and it
 * works because no code path accepts an id from a client.
 *
 * <p>All mapping to DTOs happens inside the transaction. The profile's
 * collections are lazy, so mapping outside it would fail to initialise them.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * The account and its profile in one response.
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId) {
        User user = findUser(userId);
        return CurrentUserResponse.of(user, findProfile(userId));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        return UserProfileResponse.from(findProfile(userId));
    }

    /**
     * Replaces the profile's editable fields.
     *
     * <p>Full replacement: an omitted field is cleared and an omitted collection
     * empties. Removing a skill is therefore just leaving it out, which is why
     * there is no separate delete endpoint.
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
        UserProfile profile = findProfile(userId);

        profile.update(
                trimToNull(request.headline()),
                trimToNull(request.summary()),
                request.yearsOfExperience(),
                trimToNull(request.currentRole()),
                request.remotePreference(),
                request.skills(),
                request.preferredRoles(),
                request.preferredLocations());

        log.info("Updated profile for user {}", userId);
        return UserProfileResponse.from(profile);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * A profile is created with the account, so a missing one is not a client
     * error -- it means the user is gone, or that an account was created without
     * going through registration.
     */
    private UserProfile findProfile(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
