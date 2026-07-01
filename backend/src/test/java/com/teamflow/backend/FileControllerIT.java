package com.teamflow.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamflow.backend.file.ProjectFileRepository;
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
class FileControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectFileRepository projectFileRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        projectFileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void uploadThenListAndDownload() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        MockMultipartFile file = new MockMultipartFile(
                "file", "spec.txt", "text/plain", "hello world".getBytes());
        String body = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("projectId", String.valueOf(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("spec.txt")))
                .andExpect(jsonPath("$.size", is(11)))
                .andExpect(jsonPath("$.previewable", is(false)))
                .andExpect(jsonPath("$.previewUrl", is(nullValue())))
                .andExpect(jsonPath("$.downloadUrl", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        long fileId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/projects/{id}/files", project)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) fileId)));

        mockMvc.perform(get("/api/files/{id}/download", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string("hello world"));
    }

    @Test
    void previewServesImagesInlineButRejectsNonPreviewable() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");

        long imageId = upload(owner, project, "pic.png", "image/png", new byte[] {1, 2, 3});
        mockMvc.perform(get("/api/files/{id}/preview", imageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));

        long textId = upload(owner, project, "notes.txt", "text/plain", "x".getBytes());
        mockMvc.perform(get("/api/files/{id}/preview", textId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonMemberCannotDownloadOrUpload() throws Exception {
        String owner = authenticate("owner@example.com");
        String outsider = authenticate("outsider@example.com");
        long ws = createWorkspace(owner, "Acme");
        long project = createProject(owner, ws, "Website");
        long fileId = upload(owner, project, "spec.txt", "text/plain", "data".getBytes());

        mockMvc.perform(get("/api/files/{id}/download", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", "x".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("projectId", String.valueOf(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fileEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/files/1/download")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private long upload(String token, long projectId, String name, String type, byte[] bytes)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, type, bytes);
        String body = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
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
