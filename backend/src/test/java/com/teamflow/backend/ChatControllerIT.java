package com.teamflow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamflow.backend.chat.ChatMessageRepository;
import com.teamflow.backend.notification.NotificationRepository;
import com.teamflow.backend.notification.NotificationType;
import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.UserRepository;
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
class ChatControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        chatMessageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void sendMessageThenReadHistory() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(post("/api/projects/{id}/chat/messages", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello team"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", is("Hello team")))
                .andExpect(jsonPath("$.sender.username", is("owner")));

        mockMvc.perform(get("/api/projects/{id}/chat/messages", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content", is("Hello team")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");
        for (int i = 1; i <= 3; i++) {
            sendMessage(owner, project, "Message " + i);
        }

        mockMvc.perform(get("/api/projects/{id}/chat/messages", project)
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].content", is("Message 3")))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void emptyMessageIsRejected() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(post("/api/projects/{id}/chat/messages", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentionNotifiesTaggedMember() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        String bob = joinWorkspace(owner, ws, "bob@example.com");
        long project = createProject(owner, ws, "Website");

        sendMessage(owner, project, "Hey @bob please review this");

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId("bob@example.com")))
                .filteredOn(n -> n.getType() == NotificationType.CHAT_MENTION)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getMessage()).contains("owner").contains("Website");
                    assertThat(n.getWorkspaceId()).isEqualTo(ws);
                    assertThat(n.isRead()).isFalse();
                });

        // Author mentioning themselves creates no notification.
        sendMessage(owner, project, "note to @owner self");
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId("owner@example.com")))
                .noneMatch(n -> n.getType() == NotificationType.CHAT_MENTION);
        // A non-member mention is ignored.
        sendMessage(owner, project, "cc @ghost");
    }

    @Test
    void uploadAttachmentThenSendWithIt() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        String body = mockMvc.perform(multipart("/api/projects/{id}/chat/attachments", project)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("notes.txt")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");

        mockMvc.perform(post("/api/projects/{id}/chat/messages", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attachmentUrl":"%s","attachmentName":"notes.txt"}""".formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentUrl", is(url)))
                .andExpect(jsonPath("$.attachmentName", is("notes.txt")));
    }

    @Test
    void nonMemberCannotAccessChat() throws Exception {
        String owner = authenticate("owner@example.com");
        String outsider = authenticate("outsider@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(get("/api/projects/{id}/chat/messages", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects/1/chat/messages")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private void sendMessage(String token, long projectId, String content) throws Exception {
        mockMvc.perform(post("/api/projects/{id}/chat/messages", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}""".formatted(content)))
                .andExpect(status().isCreated());
    }

    private String authenticate(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","username":"%s","password":"password123"}"""
                                .formatted(email, email.substring(0, email.indexOf('@')))))
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

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private long createWorkspace(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String joinWorkspace(String ownerToken, long workspaceId, String email) throws Exception {
        String token = authenticate(email);
        mockMvc.perform(post("/api/workspaces/{id}/invite", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        return token;
    }

    private long createProject(String token, long workspaceId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{id}/projects", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
