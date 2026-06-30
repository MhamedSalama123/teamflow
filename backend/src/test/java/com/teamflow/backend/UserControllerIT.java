package com.teamflow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    /** Registers, verifies, and returns a bearer access token for the new account. */
    private String authenticate(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","username":"user","password":"password123"}""".formatted(email)))
                .andExpect(status().isCreated());
        String code = userRepository.findByEmail(email).orElseThrow().getVerificationCode();
        String body = mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}""".formatted(email, code)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheCurrentProfile() throws Exception {
        String token = authenticate("ann@example.com");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("ann@example.com")))
                .andExpect(jsonPath("$.emailVerified", is(true)));
    }

    @Test
    void updateProfilePersistsEditableFields() throws Exception {
        String token = authenticate("ben@example.com");

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ben Carter","bio":"Builder","jobTitle":"Engineer","location":"Cairo","phoneNumber":"+20100"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Ben Carter")))
                .andExpect(jsonPath("$.bio", is("Builder")))
                .andExpect(jsonPath("$.jobTitle", is("Engineer")));

        assertThat(userRepository.findByEmail("ben@example.com").orElseThrow().getLocation()).isEqualTo("Cairo");
    }

    @Test
    void uploadPhotoReturnsUrl() throws Exception {
        String token = authenticate("cara@example.com");

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl", startsWith("/uploads/")));
    }

    @Test
    void uploadPhotoRejectsNonImage() throws Exception {
        String token = authenticate("dan@example.com");

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1, 2, 3}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        String token = authenticate("eve@example.com");

        mockMvc.perform(put("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrongpass","newPassword":"newpassword456"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordAllowsLoginWithNewPassword() throws Exception {
        String token = authenticate("finn@example.com");

        mockMvc.perform(put("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"newpassword456"}"""))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"finn@example.com","password":"newpassword456"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void changeEmailMarksAccountUnverified() throws Exception {
        String token = authenticate("gail@example.com");

        mockMvc.perform(put("/api/users/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newEmail":"gail.new@example.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("gail.new@example.com")))
                .andExpect(jsonPath("$.emailVerified", is(false)));

        assertThat(userRepository.findByEmail("gail.new@example.com")).isPresent();
        assertThat(userRepository.findByEmail("gail@example.com")).isEmpty();
    }

    @Test
    void deleteAccountSoftDeletesAndBlocksFurtherAccess() throws Exception {
        String token = authenticate("hugo@example.com");

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // The row is kept (soft delete) but the token no longer authenticates.
        assertThat(userRepository.findByEmail("hugo@example.com").orElseThrow().getDeletedAt()).isNotNull();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    /** Persists a verified, active directory user directly, bypassing the registration flow. */
    private void seedUser(String username, String fullName, String jobTitle, String location) {
        userRepository.save(User.builder()
                .email(username + "@example.com")
                .username(username)
                .password("unused")
                .emailVerified(true)
                .fullName(fullName)
                .jobTitle(jobTitle)
                .location(location)
                .build());
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/search").param("q", "a")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchMatchesUsernameOrFullNameCaseInsensitively() throws Exception {
        String token = authenticate("searcher@example.com");
        seedUser("alice", "Alice Wonder", "Engineer", "Cairo");
        seedUser("bob", "Bob Smith", "Designer", "Berlin");

        // Partial, case-insensitive match on the username.
        mockMvc.perform(get("/api/users/search").param("q", "ALI").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("alice")))
                .andExpect(jsonPath("$.content[0].fullName", is("Alice Wonder")))
                .andExpect(jsonPath("$.totalElements", is(1)));

        // Partial match on the full name.
        mockMvc.perform(get("/api/users/search").param("q", "smith").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("bob")));
    }

    @Test
    void searchFiltersByJobTitleAndLocation() throws Exception {
        String token = authenticate("searcher@example.com");
        seedUser("alice", "Alice Wonder", "Engineer", "Cairo");
        seedUser("bob", "Bob Smith", "Designer", "Berlin");
        seedUser("carol", "Carol Jones", "Engineer", "Cairo");

        mockMvc.perform(get("/api/users/search").param("jobTitle", "engineer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].username", containsInAnyOrder("alice", "carol")));

        mockMvc.perform(get("/api/users/search").param("location", "berlin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("bob")));
    }

    @Test
    void searchExcludesSoftDeletedUsers() throws Exception {
        String token = authenticate("searcher@example.com");
        userRepository.save(User.builder()
                .email("ghost@example.com").username("ghost").password("unused").emailVerified(true)
                .fullName("Ghost User").deletedAt(Instant.now()).build());

        mockMvc.perform(get("/api/users/search").param("q", "ghost")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void searchPaginatesResults() throws Exception {
        String token = authenticate("searcher@example.com");
        seedUser("qa1", "Q A One", "QA", "Remote");
        seedUser("qa2", "Q A Two", "QA", "Remote");
        seedUser("qa3", "Q A Three", "QA", "Remote");

        mockMvc.perform(get("/api/users/search").param("jobTitle", "qa").param("page", "0").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].username", containsInAnyOrder("qa1", "qa2")))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(2)));

        mockMvc.perform(get("/api/users/search").param("jobTitle", "qa").param("page", "1").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("qa3")));
    }
}
