package com.teamflow.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.teamflow.backend.project.TaskEvent;
import com.teamflow.backend.project.TaskEvent.TaskEventType;
import com.teamflow.backend.project.TaskRepository;
import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.UserRepository;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WebSocketIT {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void memberReceivesTaskEventsForSubscribedProject() throws Exception {
        String token = authenticate("wsowner@example.com");
        long workspace = createWorkspace(token, "Acme");
        long project = createProject(token, workspace, "Website");

        StompSession session = connect(token);
        BlockingQueue<TaskEvent> events = new LinkedBlockingQueue<>();
        session.subscribe("/topic/projects/" + project, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((TaskEvent) payload);
            }
        });
        // Give the SUBSCRIBE frame time to be processed before mutating tasks.
        Thread.sleep(400);

        long taskId = createTask(token, project, "Design");
        TaskEvent created = events.poll(5, TimeUnit.SECONDS);
        assertThat(created).isNotNull();
        assertThat(created.type()).isEqualTo(TaskEventType.CREATED);
        assertThat(created.taskId()).isEqualTo(taskId);

        rest.exchange(
                url("/api/projects/" + project + "/tasks/" + taskId),
                HttpMethod.PUT,
                new HttpEntity<>(
                        """
                        {"title":"Design v2","status":"IN_PROGRESS","priority":"HIGH"}""",
                        jsonHeaders(token)),
                String.class);
        TaskEvent updated = events.poll(5, TimeUnit.SECONDS);
        assertThat(updated).isNotNull();
        assertThat(updated.type()).isEqualTo(TaskEventType.UPDATED);

        session.disconnect();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        WebSocketStompClient client = stompClient();
        assertThatThrownBy(() -> client
                .connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        new StompHeaders(),
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    // --- helpers ---

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient()
                .connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private ResponseEntity<String> post(String path, String json, String token) {
        return rest.postForEntity(url(path), new HttpEntity<>(json, jsonHeaders(token)), String.class);
    }

    private String authenticate(String email) {
        String username = email.substring(0, email.indexOf('@'));
        post("/api/auth/register",
                """
                {"email":"%s","username":"%s","password":"password123"}""".formatted(email, username),
                null);
        String code = userRepository.findByEmail(email).orElseThrow().getVerificationCode();
        ResponseEntity<String> verified = post("/api/auth/verify-email",
                """
                {"email":"%s","code":"%s"}""".formatted(email, code), null);
        return JsonPath.read(verified.getBody(), "$.accessToken");
    }

    private long createWorkspace(String token, String name) {
        ResponseEntity<String> response = post("/api/workspaces",
                """
                {"name":"%s"}""".formatted(name), token);
        return ((Number) JsonPath.read(response.getBody(), "$.id")).longValue();
    }

    private long createProject(String token, long workspaceId, String name) {
        ResponseEntity<String> response = post("/api/workspaces/" + workspaceId + "/projects",
                """
                {"name":"%s"}""".formatted(name), token);
        return ((Number) JsonPath.read(response.getBody(), "$.id")).longValue();
    }

    private long createTask(String token, long projectId, String title) {
        ResponseEntity<String> response = post("/api/projects/" + projectId + "/tasks",
                """
                {"title":"%s"}""".formatted(title), token);
        return ((Number) JsonPath.read(response.getBody(), "$.id")).longValue();
    }
}
