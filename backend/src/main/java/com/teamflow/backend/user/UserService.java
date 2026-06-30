package com.teamflow.backend.user;

import com.teamflow.backend.auth.EmailAlreadyUsedException;
import com.teamflow.backend.auth.EmailVerificationService;
import com.teamflow.backend.user.dto.ChangeEmailRequest;
import com.teamflow.backend.user.dto.ChangePasswordRequest;
import com.teamflow.backend.user.dto.PagedResponse;
import com.teamflow.backend.user.dto.UpdateProfileRequest;
import com.teamflow.backend.user.dto.UserProfileResponse;
import com.teamflow.backend.user.dto.UserSearchResult;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final EmailVerificationService emailVerificationService;

    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        return UserProfileResponse.from(requireActiveUser(email));
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserSearchResult> search(
            String q, String jobTitle, String location, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("username").ascending());
        return PagedResponse.from(
                userRepository.search(blankToNull(q), blankToNull(jobTitle), blankToNull(location), pageable)
                        .map(UserSearchResult::from));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = requireActiveUser(email);
        user.setFullName(request.fullName());
        user.setBio(request.bio());
        user.setJobTitle(request.jobTitle());
        user.setLocation(request.location());
        user.setPhoneNumber(request.phoneNumber());
        return UserProfileResponse.from(userRepository.save(user));
    }

    @Transactional
    public String updatePhoto(String email, MultipartFile file) {
        User user = requireActiveUser(email);
        String photoUrl = fileStorageService.store(file);
        user.setPhotoUrl(photoUrl);
        userRepository.save(user);
        return photoUrl;
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = requireActiveUser(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IncorrectPasswordException();
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /**
     * Changes the email and restarts verification against the new address. The account is marked
     * unverified, so the current session's token (issued for the old email) stops working and the
     * user must verify the new email before logging in again.
     */
    @Transactional
    public UserProfileResponse changeEmail(String email, ChangeEmailRequest request) {
        User user = requireActiveUser(email);
        String newEmail = request.newEmail();
        if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyUsedException(newEmail);
        }
        user.setEmail(newEmail);
        user.setEmailVerified(false);
        emailVerificationService.startVerification(user);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void deleteAccount(String email) {
        User user = requireActiveUser(email);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    private User requireActiveUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(UserNotFoundException::new);
    }
}
