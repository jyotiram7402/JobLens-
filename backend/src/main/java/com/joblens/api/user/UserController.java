package com.joblens.api.user;

import com.joblens.api.common.web.ApiRoutes;
import com.joblens.api.security.CurrentUser;
import com.joblens.api.user.dto.CurrentUserResponse;
import com.joblens.api.user.dto.UpdateUserProfileRequest;
import com.joblens.api.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The signed-in user's own account and profile. Every route here requires
 * authentication.
 *
 * <p><b>Every path says {@code /me}, and that is a security decision rather than
 * a naming one.</b> There is no {@code /users/{id}} endpoint and no
 * {@code userId} field in any request body, so there is nothing for a caller to
 * tamper with: the record being read or written is whichever one the verified
 * token points at. Insecure direct object reference is not defended against
 * here, it is designed out, because the only id in play never comes from the
 * client.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1 + "/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * The signed-in user's account and profile together, which is what a client
     * needs on load.
     */
    @GetMapping
    public CurrentUserResponse getCurrentUser() {
        return userService.getCurrentUser(currentUserId());
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile() {
        return userService.getProfile(currentUserId());
    }

    /**
     * Replaces the signed-in user's profile. Full replacement, so an omitted
     * field is cleared.
     */
    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        return userService.updateProfile(currentUserId(), request);
    }

    /**
     * The one source of the caller's identity in this controller.
     */
    private static UUID currentUserId() {
        return CurrentUser.require().id();
    }
}
