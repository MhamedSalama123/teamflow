package com.teamflow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesUserAndReturnsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","username":"alice","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    void registerStoresHashedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com","username":"bob","password":"password123"}"""))
                .andExpect(status().isCreated());

        String stored = userRepository.findByEmail("bob@example.com").orElseThrow().getPassword();
        assertThat(stored).isNotEqualTo("password123");
        assertThat(stored).startsWith("$2");
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String body = """
                {"email":"carol@example.com","username":"carol","password":"password123"}""";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
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
    void loginReturnsTokensForValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dave@example.com","username":"dave","password":"password123"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dave@example.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"erin@example.com","username":"erin","password":"password123"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"erin@example.com","password":"wrongpassword"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsUnknownUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"password123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleCallbackCreatesUserAndReturnsTokens() throws Exception {
        given(googleOAuthClient.exchangeCodeForUser("auth-code"))
                .willReturn(new GoogleUserInfo("grace@example.com", "Grace Hopper"));

        mockMvc.perform(get("/api/auth/google/callback").param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));

        assertThat(userRepository.findByEmail("grace@example.com")).isPresent();
    }

    @Test
    void googleCallbackReusesExistingAccountByEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"heidi@example.com","username":"heidi","password":"password123"}"""))
                .andExpect(status().isCreated());

        given(googleOAuthClient.exchangeCodeForUser("auth-code"))
                .willReturn(new GoogleUserInfo("heidi@example.com", "Heidi"));

        mockMvc.perform(get("/api/auth/google/callback").param("code", "auth-code"))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll()).hasSize(1);
    }
}
