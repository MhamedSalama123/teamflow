package com.teamflow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.security.GoogleOAuthClient;
import com.teamflow.backend.security.GoogleUserInfo;
import com.teamflow.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private void register(String email, String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","username":"%s","password":"%s"}"""
                                .formatted(email, username, password)))
                .andExpect(status().isCreated());
    }

    private String storedCode(String email) {
        return userRepository.findByEmail(email).orElseThrow().getVerificationCode();
    }

    private void verify(String email, String code, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}""".formatted(email, code)))
                .andExpect(expected);
    }

    @Test
    void registerSendsCodeAndWithholdsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","username":"alice","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        var user = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getVerificationCode()).matches("\\d{6}");
    }

    @Test
    void registerStoresHashedPassword() throws Exception {
        register("bob@example.com", "bob", "password123");

        String stored = userRepository.findByEmail("bob@example.com").orElseThrow().getPassword();
        assertThat(stored).isNotEqualTo("password123").startsWith("$2");
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        register("carol@example.com", "carol", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carol@example.com","username":"carol","password":"password123"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","username":"x","password":"short"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailMarksUserVerifiedAndReturnsTokens() throws Exception {
        register("dave@example.com", "dave", "password123");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dave@example.com","code":"%s"}""".formatted(storedCode("dave@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));

        assertThat(userRepository.findByEmail("dave@example.com").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmailRejectsWrongCode() throws Exception {
        register("erin@example.com", "erin", "password123");
        String wrong = "000000".equals(storedCode("erin@example.com")) ? "111111" : "000000";

        verify("erin@example.com", wrong, status().isBadRequest());

        assertThat(userRepository.findByEmail("erin@example.com").orElseThrow().getVerificationAttempts())
                .isEqualTo(1);
    }

    @Test
    void lockoutBlocksVerificationAfterThreeFailures() throws Exception {
        register("frank@example.com", "frank", "password123");
        String code = storedCode("frank@example.com");
        String wrong = "000000".equals(code) ? "111111" : "000000";

        verify("frank@example.com", wrong, status().isBadRequest());
        verify("frank@example.com", wrong, status().isBadRequest());
        verify("frank@example.com", wrong, status().isTooManyRequests());

        // Even the correct code is rejected while locked out.
        verify("frank@example.com", code, status().isTooManyRequests());
    }

    @Test
    void loginIsBlockedUntilEmailIsVerified() throws Exception {
        register("grace@example.com", "grace", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","password":"password123"}"""))
                .andExpect(status().isForbidden());

        verify("grace@example.com", storedCode("grace@example.com"), status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"grace@example.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        register("heidi@example.com", "heidi", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"heidi@example.com","password":"wrongpassword"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resendIssuesANewCode() throws Exception {
        register("ivan@example.com", "ivan", "password123");
        String firstCode = storedCode("ivan@example.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ivan@example.com"}"""))
                .andExpect(status().isAccepted());

        String secondCode = storedCode("ivan@example.com");
        assertThat(secondCode).matches("\\d{6}").isNotEqualTo(firstCode);
        verify("ivan@example.com", secondCode, status().isOk());
    }

    @Test
    void googleCallbackCreatesVerifiedUserAndReturnsTokens() throws Exception {
        given(googleOAuthClient.exchangeCodeForUser("auth-code"))
                .willReturn(new GoogleUserInfo("judy@example.com", "Judy"));

        mockMvc.perform(get("/api/auth/google/callback").param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));

        var user = userRepository.findByEmail("judy@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void googleCallbackReusesExistingAccountByEmail() throws Exception {
        register("ken@example.com", "ken", "password123");

        given(googleOAuthClient.exchangeCodeForUser("auth-code"))
                .willReturn(new GoogleUserInfo("ken@example.com", "Ken"));

        mockMvc.perform(get("/api/auth/google/callback").param("code", "auth-code"))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userRepository.findByEmail("ken@example.com").orElseThrow().isEmailVerified()).isTrue();
    }
}
