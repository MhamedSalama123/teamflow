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
import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.UserRepository;
import com.teamflow.backend.workspace.WorkspaceInvitationRepository;
import com.teamflow.backend.workspace.WorkspaceMemberRepository;
import com.teamflow.backend.workspace.WorkspaceMemberStatus;
import com.teamflow.backend.workspace.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
class WorkspaceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository memberRepository;

    @Autowired
    private WorkspaceInvitationRepository invitationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        memberRepository.deleteAll();
        invitationRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Registers, verifies, and returns a bearer access token for the new account. */
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

    /** Creates a workspace owned by the token holder and returns its id. */
    private long createWorkspace(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("OWNER")))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void invite(String ownerToken, long workspaceId, String email) throws Exception {
        mockMvc.perform(post("/api/workspaces/{id}/invite", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("INVITED")));
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createMakesCallerTheOwnerAndListsIt() throws Exception {
        String token = authenticate("owner@example.com");
        createWorkspace(token, "Acme");

        mockMvc.perform(get("/api/workspaces/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Acme")))
                .andExpect(jsonPath("$[0].role", is("OWNER")))
                .andExpect(jsonPath("$[0].memberCount", is(1)));
    }

    @Test
    void inviteCreatesPendingMembershipAndNotification() throws Exception {
        String owner = authenticate("owner@example.com");
        authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");

        invite(owner, ws, "bob@example.com");

        assertThat(memberRepository.findByWorkspaceIdAndUserId(ws, userId("bob@example.com")))
                .get()
                .satisfies(m -> {
                    assertThat(m.getStatus()).isEqualTo(WorkspaceMemberStatus.INVITED);
                    assertThat(m.getRole()).isEqualTo(WorkspaceRole.MEMBER);
                });
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId("bob@example.com")))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.WORKSPACE_INVITATION);
                    assertThat(n.getWorkspaceId()).isEqualTo(ws);
                });
        Mockito.verify(emailService)
                .sendWorkspaceInvitation(Mockito.eq("bob@example.com"), Mockito.anyString(), Mockito.eq("Acme"));
    }

    @Test
    void inviteUnregisteredEmailStoresPendingInvitation() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");

        // An email with no account yet yields a pending invitation, not a member row.
        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newcomer@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("INVITED")))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.email", is("newcomer@example.com")));

        // It surfaces on the workspace detail under pendingInvitations, not members.
        mockMvc.perform(get("/api/workspaces/{id}", ws).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.pendingInvitations", hasSize(1)))
                .andExpect(jsonPath("$.pendingInvitations[0].email", is("newcomer@example.com")));
    }

    @Test
    void invitingSameUnregisteredEmailTwiceConflicts() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newcomer@example.com"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newcomer@example.com"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void pendingInvitationIsClaimedWhenInviteeRegisters() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newcomer@example.com"}"""))
                .andExpect(status().isCreated());

        // The invitee registers and verifies: the pending invite becomes an invited membership
        // plus an in-app notification, and the standalone pending invitation is consumed.
        String invitee = authenticate("newcomer@example.com");

        assertThat(memberRepository.findByWorkspaceIdAndUserId(ws, userId("newcomer@example.com")))
                .get()
                .satisfies(m -> assertThat(m.getStatus()).isEqualTo(WorkspaceMemberStatus.INVITED));
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId("newcomer@example.com")))
                .singleElement()
                .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.WORKSPACE_INVITATION));

        // The invitee can accept through the normal flow and then sees the workspace.
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitee)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workspaces/me").header(HttpHeaders.AUTHORIZATION, bearer(invitee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/workspaces/{id}", ws).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingInvitations", hasSize(0)));
    }

    @Test
    void cancelPendingInvitationRemovesIt() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");
        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newcomer@example.com"}"""))
                .andExpect(status().isCreated());
        long invitationId = invitationRepository
                .findByWorkspaceIdOrderByCreatedAtAsc(ws).get(0).getId();

        mockMvc.perform(delete("/api/workspaces/{id}/invitations/{invitationId}", ws, invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workspaces/{id}", ws).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingInvitations", hasSize(0)));
    }

    @Test
    void inviteRequiresOwnerOrAdmin() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        authenticate("carol@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());

        // Bob is now a plain MEMBER and may not invite others.
        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carol@example.com"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanInviteRegisteredUserDirectlyAsAdmin() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        authenticate("carol@example.com");
        long ws = createWorkspace(owner, "Acme");

        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com","role":"ADMIN"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ADMIN")));

        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());
        // Bob came in as an admin, so he may already invite others.
        invite(bob, ws, "carol@example.com");
    }

    @Test
    void ownerCanInviteUnregisteredEmailAsAdmin() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");

        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newcomer@example.com","role":"ADMIN"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ADMIN")));

        // When the invitee registers, the admin role carries over to the membership.
        authenticate("newcomer@example.com");
        assertThat(memberRepository.findByWorkspaceIdAndUserId(ws, userId("newcomer@example.com")))
                .get()
                .satisfies(m -> assertThat(m.getRole()).isEqualTo(WorkspaceRole.ADMIN));
    }

    @Test
    void adminCannotInviteAsAdmin() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        authenticate("carol@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/workspaces/{id}/members/{userId}/role", ws, userId("bob@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk());

        // An admin may invite, but only as a plain member.
        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carol@example.com","role":"ADMIN"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteAsOwnerRoleIsRejected() throws Exception {
        String owner = authenticate("owner@example.com");
        authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");

        mockMvc.perform(post("/api/workspaces/{id}/invite", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com","role":"OWNER"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptInviteActivatesMembership() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");

        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("MEMBER")));

        mockMvc.perform(get("/api/workspaces/me").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) ws)));
    }

    @Test
    void declineInviteRemovesMembership() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");

        mockMvc.perform(post("/api/workspaces/{id}/invite/decline", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.findByWorkspaceIdAndUserId(ws, userId("bob@example.com"))).isEmpty();
    }

    @Test
    void removeMemberByOwner() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/workspaces/{id}/members/{userId}", ws, userId("bob@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.findByWorkspaceIdAndUserId(ws, userId("bob@example.com"))).isEmpty();
    }

    @Test
    void removeMemberForbiddenForPlainMember() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/workspaces/{id}/members/{userId}", ws, userId("owner@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotRemoveLastOwner() throws Exception {
        String owner = authenticate("owner@example.com");
        long ws = createWorkspace(owner, "Acme");

        mockMvc.perform(delete("/api/workspaces/{id}/members/{userId}", ws, userId("owner@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeRolePromotesMemberToAdmin() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        authenticate("carol@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/workspaces/{id}/members/{userId}/role", ws, userId("bob@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")));

        // As an admin, Bob may now invite others.
        invite(bob, ws, "carol@example.com");
    }

    @Test
    void adminCannotChangeAnotherAdminsRole() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        String carol = authenticate("carol@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());
        invite(owner, ws, "carol@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(carol)))
                .andExpect(status().isOk());
        // Promote both Bob and Carol to admin.
        mockMvc.perform(put("/api/workspaces/{id}/members/{userId}/role", ws, userId("bob@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/workspaces/{id}/members/{userId}/role", ws, userId("carol@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk());

        // Bob (admin) cannot change Carol (admin).
        mockMvc.perform(put("/api/workspaces/{id}/members/{userId}/role", ws, userId("carol@example.com"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailListsMembersForMember() throws Exception {
        String owner = authenticate("owner@example.com");
        String bob = authenticate("bob@example.com");
        long ws = createWorkspace(owner, "Acme");
        invite(owner, ws, "bob@example.com");
        mockMvc.perform(post("/api/workspaces/{id}/invite/accept", ws)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workspaces/{id}", ws).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Acme")))
                .andExpect(jsonPath("$.role", is("MEMBER")))
                .andExpect(jsonPath("$.members", hasSize(2)));
    }

    @Test
    void detailForbiddenForNonMember() throws Exception {
        String owner = authenticate("owner@example.com");
        String outsider = authenticate("outsider@example.com");
        long ws = createWorkspace(owner, "Acme");

        mockMvc.perform(get("/api/workspaces/{id}", ws).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
    }
}
