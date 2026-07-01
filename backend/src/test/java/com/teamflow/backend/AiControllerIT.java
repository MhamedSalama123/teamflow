package com.teamflow.backend;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamflow.backend.ai.ClaudeClient;
import com.teamflow.backend.chat.ChatMessageRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ClaudeClient claudeClient;

    @BeforeEach
    void cleanUp() {
        chatMessageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void summarizeReturnsClaudeOutput() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");
        sendChat(owner, project, "We shipped the login page today.");

        when(claudeClient.complete(contains("summarize"), contains("shipped the login page"), anyLong()))
                .thenReturn("The team shipped the login page.");

        mockMvc.perform(post("/api/ai/summarize")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d}""".formatted(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary", is("The team shipped the login page.")));
    }

    @Test
    void summarizeEmptyChatSkipsTheModel() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(post("/api/ai/summarize")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d}""".formatted(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary", is("There are no chat messages in this project yet.")));
    }

    @Test
    void generateTasksParsesJsonAndNormalizesPriority() throws Exception {
        String owner = authenticate("owner@example.com");
        // The model may wrap the array in prose / markdown fences — the service must still parse it.
        when(claudeClient.complete(contains("actionable tasks"), eq("Build a landing page"), anyLong()))
                .thenReturn("""
                        Here you go:
                        ```json
                        [{"title":"Design hero","priority":"HIGH","dueDate":"2026-08-01"},
                         {"title":"Write copy","priority":"bogus","dueDate":null}]
                        ```""");

        mockMvc.perform(post("/api/ai/generate-tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Build a landing page"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath("$.tasks[0].title", is("Design hero")))
                .andExpect(jsonPath("$.tasks[0].priority", is("HIGH")))
                .andExpect(jsonPath("$.tasks[0].dueDate", is("2026-08-01")))
                .andExpect(jsonPath("$.tasks[1].priority", is("MEDIUM")));
    }

    @Test
    void askReturnsClaudeAnswer() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        when(claudeClient.complete(contains("answer questions"), contains("What is left to do?"), anyLong()))
                .thenReturn("Nothing — the board is empty.");

        mockMvc.perform(post("/api/ai/ask")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d,"question":"What is left to do?"}""".formatted(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("Nothing — the board is empty.")));
    }

    @Test
    void nonMemberCannotUseProjectAiEndpoints() throws Exception {
        String owner = authenticate("owner@example.com");
        String outsider = authenticate("outsider@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(post("/api/ai/summarize")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":%d}""".formatted(project)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aiEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai/generate-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"x"}"""))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private void sendChat(String token, long projectId, String content) throws Exception {
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
