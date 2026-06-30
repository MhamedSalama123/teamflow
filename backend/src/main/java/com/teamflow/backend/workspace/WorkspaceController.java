package com.teamflow.backend.workspace;

import com.teamflow.backend.workspace.dto.ChangeRoleRequest;
import com.teamflow.backend.workspace.dto.CreateWorkspaceRequest;
import com.teamflow.backend.workspace.dto.InviteMemberRequest;
import com.teamflow.backend.workspace.dto.WorkspaceDetailResponse;
import com.teamflow.backend.workspace.dto.WorkspaceMemberResponse;
import com.teamflow.backend.workspace.dto.WorkspaceResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            Authentication authentication, @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(authentication.getName(), request);
    }

    @GetMapping("/me")
    public List<WorkspaceResponse> myWorkspaces(Authentication authentication) {
        return workspaceService.listForUser(authentication.getName());
    }

    @GetMapping("/{id}")
    public WorkspaceDetailResponse detail(Authentication authentication, @PathVariable Long id) {
        return workspaceService.getDetail(authentication.getName(), id);
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse invite(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody InviteMemberRequest request) {
        return workspaceService.invite(authentication.getName(), id, request);
    }

    @PostMapping("/{id}/invite/accept")
    public WorkspaceResponse acceptInvite(Authentication authentication, @PathVariable Long id) {
        return workspaceService.acceptInvite(authentication.getName(), id);
    }

    @PostMapping("/{id}/invite/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvite(Authentication authentication, @PathVariable Long id) {
        workspaceService.declineInvite(authentication.getName(), id);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            Authentication authentication, @PathVariable Long id, @PathVariable Long userId) {
        workspaceService.removeMember(authentication.getName(), id, userId);
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(
            Authentication authentication, @PathVariable Long id, @PathVariable Long invitationId) {
        workspaceService.cancelInvitation(authentication.getName(), id, invitationId);
    }

    @PutMapping("/{id}/members/{userId}/role")
    public WorkspaceMemberResponse changeRole(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return workspaceService.changeRole(authentication.getName(), id, userId, request);
    }
}
