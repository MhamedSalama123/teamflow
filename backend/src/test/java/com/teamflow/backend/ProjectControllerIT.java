package com.teamflow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamflow.backend.notification.NotificationRepository;
import com.teamflow.backend.notification.NotificationType;
import com.teamflow.backend.project.TaskRepository;
import com.teamflow.backend.project.TaskStatus;
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
class ProjectControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        // The Testcontainers Postgres is shared across test classes, so reset the rows this suite
        // touches. Deleting users cascades to memberships; tasks are cleared explicitly. Workspaces
        // and projects are scoped per test by their freshly created ids, so leftovers are harmless.
        taskRepository.deleteAll();
        userRepository.deleteAll();
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

    /** Registers {@code email}, has the owner invite them, accepts, and returns their token. */
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

    private long createTask(String token, long projectId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{id}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}""".formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("TODO")))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void createAndListProjects() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        createProject(owner, ws, "Website");

        mockMvc.perform(get("/api/workspaces/{id}/projects", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Website")));
    }

    @Test
    void plainMemberCanCreateProjectButNotDelete() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        String bob = joinWorkspace(owner, ws, "bob@example.com");

        long project = createProject(bob, ws, "Bob's Project");

        // A plain member cannot delete a project.
        mockMvc.perform(delete("/api/workspaces/{id}/projects/{pid}", ws, project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isForbidden());

        // The owner can.
        mockMvc.perform(delete("/api/workspaces/{id}/projects/{pid}", ws, project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonMemberCannotAccessProjects() throws Exception {
        String owner = authenticate("owner@example.com");
        String outsider = authenticate("outsider@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(get("/api/workspaces/{id}/projects", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/{id}/tasks", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTaskWithDetailsAndListBoard() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        mockMvc.perform(post("/api/projects/{id}/tasks", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Design home page","description":"Hero + nav","priority":"HIGH",
                                 "dueDate":"2026-08-01","assigneeId":%d}""".formatted(userId("owner@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority", is("HIGH")))
                .andExpect(jsonPath("$.dueDate", is("2026-08-01")))
                .andExpect(jsonPath("$.assignee.username", is("owner")))
                .andExpect(jsonPath("$.status", is("TODO")));

        mockMvc.perform(get("/api/projects/{id}/tasks", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Design home page")));
    }

    @Test
    void updateMovesTaskBetweenColumnsAndRenumbers() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");
        long first = createTask(owner, project, "First");
        long second = createTask(owner, project, "Second");

        // Move the first task to IN_PROGRESS.
        mockMvc.perform(put("/api/projects/{id}/tasks/{tid}", project, first)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"First","status":"IN_PROGRESS","priority":"MEDIUM"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.position", is(0)));

        // The TODO column renumbers so the remaining task is at position 0.
        assertThat(taskRepository.findByIdAndProjectId(second, project).orElseThrow().getPosition())
                .isZero();
        assertThat(taskRepository.findByProjectIdAndStatusOrderByPositionAscIdAsc(project, TaskStatus.TODO))
                .singleElement()
                .satisfies(t -> assertThat(t.getId()).isEqualTo(second));
    }

    @Test
    void assignTaskToMemberAndRejectNonMember() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        String bob = joinWorkspace(owner, ws, "bob@example.com");
        authenticate("stranger@example.com");
        long project = createProject(owner, ws, "Website");
        long task = createTask(owner, project, "Task");

        mockMvc.perform(put("/api/projects/{id}/tasks/{tid}/assign", project, task)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}""".formatted(userId("bob@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.username", is("bob")));

        // Someone who is not a member of the workspace cannot be assigned.
        mockMvc.perform(put("/api/projects/{id}/tasks/{tid}/assign", project, task)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}""".formatted(userId("stranger@example.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTaskRemovesIt() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");
        long task = createTask(owner, project, "Throwaway");

        mockMvc.perform(delete("/api/projects/{id}/tasks/{tid}", project, task)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.findByIdAndProjectId(task, project)).isEmpty();
    }

    @Test
    void assigningTaskToAnotherMemberNotifiesThem() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        String bob = joinWorkspace(owner, ws, "bob@example.com");
        long project = createProject(owner, ws, "Website");
        long task = createTask(owner, project, "Ship it");

        mockMvc.perform(put("/api/projects/{id}/tasks/{tid}/assign", project, task)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}""".formatted(userId("bob@example.com"))))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId("bob@example.com")))
                .filteredOn(n -> n.getType() == NotificationType.TASK_ASSIGNED)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getMessage()).contains("Ship it").contains("Website");
                    assertThat(n.getWorkspaceId()).isEqualTo(ws);
                    assertThat(n.isRead()).isFalse();
                });

        // The assignee can read it back through the notifications API.
        mockMvc.perform(get("/api/notifications/me").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'TASK_ASSIGNED')]", hasSize(1)));
    }

    @Test
    void selfAssignmentDoesNotNotify() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");
        long task = createTask(owner, project, "Mine");

        mockMvc.perform(put("/api/projects/{id}/tasks/{tid}/assign", project, task)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}""".formatted(userId("owner@example.com"))))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId("owner@example.com")))
                .noneMatch(n -> n.getType() == NotificationType.TASK_ASSIGNED);
    }

    @Test
    void taskEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects/1/tasks")).andExpect(status().isUnauthorized());
    }
}
