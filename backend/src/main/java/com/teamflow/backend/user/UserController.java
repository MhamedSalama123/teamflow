package com.teamflow.backend.user;

import com.teamflow.backend.user.dto.ChangeEmailRequest;
import com.teamflow.backend.user.dto.ChangePasswordRequest;
import com.teamflow.backend.user.dto.PagedResponse;
import com.teamflow.backend.user.dto.PhotoUploadResponse;
import com.teamflow.backend.user.dto.UpdateProfileRequest;
import com.teamflow.backend.user.dto.UserProfileResponse;
import com.teamflow.backend.user.dto.UserSearchResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service profile endpoints. The current user is taken from the authenticated principal
 * (the JWT subject), so callers can only ever read or modify their own account.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }

    @GetMapping("/search")
    public PagedResponse<UserSearchResult> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.search(q, jobTitle, location, page, size);
    }

    @PutMapping("/me")
    public UserProfileResponse updateProfile(
            Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @PostMapping("/me/photo")
    public PhotoUploadResponse uploadPhoto(
            Authentication authentication, @RequestParam("file") MultipartFile file) {
        return new PhotoUploadResponse(userService.updatePhoto(authentication.getName(), file));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
    }

    @PutMapping("/me/email")
    public UserProfileResponse changeEmail(
            Authentication authentication, @Valid @RequestBody ChangeEmailRequest request) {
        return userService.changeEmail(authentication.getName(), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Authentication authentication) {
        userService.deleteAccount(authentication.getName());
    }
}
