package com.teamflow.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamflow.backend.auth.EmailAlreadyUsedException;
import com.teamflow.backend.auth.EmailVerificationService;
import com.teamflow.backend.user.dto.ChangeEmailRequest;
import com.teamflow.backend.user.dto.ChangePasswordRequest;
import com.teamflow.backend.user.dto.UpdateProfileRequest;
import com.teamflow.backend.user.dto.UserProfileResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EmailVerificationService emailVerificationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, passwordEncoder, fileStorageService, emailVerificationService);
    }

    private User user() {
        return User.builder()
                .id(1L).email("user@example.com").username("user")
                .password(passwordEncoder.encode("password123")).emailVerified(true).build();
    }

    private void givenActiveUser(User user) {
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));
        lenientSave();
    }

    private void lenientSave() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getProfileMapsTheUser() {
        User user = user();
        user.setBio("hello");
        when(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile("user@example.com");

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.bio()).isEqualTo("hello");
        assertThat(response.emailVerified()).isTrue();
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(UserNotFoundException.class)
                .isThrownBy(() -> userService.getProfile("ghost@example.com"));
    }

    @Test
    void updateProfileSetsTheEditableFields() {
        User user = user();
        givenActiveUser(user);

        UserProfileResponse response = userService.updateProfile(
                "user@example.com",
                new UpdateProfileRequest("Ada Lovelace", "My bio", "Engineer", "Cairo", "+201234567"));

        assertThat(response.fullName()).isEqualTo("Ada Lovelace");
        assertThat(response.bio()).isEqualTo("My bio");
        assertThat(response.jobTitle()).isEqualTo("Engineer");
        assertThat(response.location()).isEqualTo("Cairo");
        assertThat(response.phoneNumber()).isEqualTo("+201234567");
    }

    @Test
    void updatePhotoStoresFileAndSavesUrl() {
        User user = user();
        givenActiveUser(user);
        when(fileStorageService.store(any())).thenReturn("/uploads/abc.png");

        String url = userService.updatePhoto(
                "user@example.com",
                new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}));

        assertThat(url).isEqualTo("/uploads/abc.png");
        assertThat(user.getPhotoUrl()).isEqualTo("/uploads/abc.png");
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = user();
        when(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(IncorrectPasswordException.class).isThrownBy(() ->
                userService.changePassword("user@example.com",
                        new ChangePasswordRequest("wrong-password", "newpassword456")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordUpdatesHashWhenCurrentMatches() {
        User user = user();
        givenActiveUser(user);

        userService.changePassword("user@example.com",
                new ChangePasswordRequest("password123", "newpassword456"));

        assertThat(passwordEncoder.matches("newpassword456", user.getPassword())).isTrue();
    }

    @Test
    void changeEmailRestartsVerification() {
        User user = user();
        // No save() stub here: the row is persisted inside the mocked startVerification(), so the
        // service itself does not call userRepository.save().
        when(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        UserProfileResponse response =
                userService.changeEmail("user@example.com", new ChangeEmailRequest("new@example.com"));

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.emailVerified()).isFalse();
        verify(emailVerificationService).startVerification(user);
    }

    @Test
    void changeEmailRejectsAddressInUse() {
        User user = user();
        when(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatExceptionOfType(EmailAlreadyUsedException.class).isThrownBy(() ->
                userService.changeEmail("user@example.com", new ChangeEmailRequest("taken@example.com")));

        verify(emailVerificationService, never()).startVerification(any());
    }

    @Test
    void deleteAccountSetsDeletedAt() {
        User user = user();
        givenActiveUser(user);

        userService.deleteAccount("user@example.com");

        assertThat(user.getDeletedAt()).isNotNull();
    }
}
