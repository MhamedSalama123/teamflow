import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import {
  Workspace,
  WorkspaceDetail,
  WorkspaceMember,
  WorkspaceRole,
} from '../../core/workspace/workspace.models';

const MANAGED_ROLES: WorkspaceRole[] = ['OWNER', 'ADMIN'];

@Component({
  selector: 'app-workspaces',
  imports: [ReactiveFormsModule],
  templateUrl: './workspaces.html',
})
export class Workspaces implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly workspaceService = inject(WorkspaceService);

  protected readonly workspaces = signal<Workspace[]>([]);
  protected readonly selected = signal<WorkspaceDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly roles: WorkspaceRole[] = ['OWNER', 'ADMIN', 'MEMBER'];

  protected readonly createForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
  });

  protected readonly inviteForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.loadWorkspaces();
  }

  /** Whether the current user can manage members of the selected workspace. */
  protected canManage(): boolean {
    const detail = this.selected();
    return detail !== null && MANAGED_ROLES.includes(detail.role);
  }

  protected create(): void {
    if (this.createForm.invalid) {
      return;
    }
    this.reset();
    this.workspaceService.create(this.createForm.getRawValue().name).subscribe({
      next: (created) => {
        this.createForm.reset({ name: '' });
        this.loadWorkspaces();
        this.select(created.id);
      },
      error: () => this.error.set('Could not create the workspace. Please try again.'),
    });
  }

  protected select(workspaceId: number): void {
    this.reset();
    this.workspaceService.detail(workspaceId).subscribe({
      next: (detail) => this.selected.set(detail),
      error: () => this.error.set('Could not load that workspace.'),
    });
  }

  protected invite(): void {
    const detail = this.selected();
    if (!detail || this.inviteForm.invalid) {
      return;
    }
    this.reset();
    this.workspaceService.invite(detail.id, this.inviteForm.getRawValue().email).subscribe({
      next: () => {
        this.inviteForm.reset({ email: '' });
        // Refresh first: select() clears transient messages, so set the notice afterwards.
        this.select(detail.id);
        this.notice.set('Invitation sent.');
      },
      error: (err) => this.error.set(this.inviteError(err.status)),
    });
  }

  protected removeMember(member: WorkspaceMember): void {
    const detail = this.selected();
    if (!detail) {
      return;
    }
    this.reset();
    this.workspaceService.removeMember(detail.id, member.userId).subscribe({
      next: () => this.select(detail.id),
      error: () => this.error.set('Could not remove that member.'),
    });
  }

  protected changeRole(member: WorkspaceMember, role: WorkspaceRole): void {
    const detail = this.selected();
    if (!detail || role === member.role) {
      return;
    }
    this.reset();
    this.workspaceService.changeRole(detail.id, member.userId, role).subscribe({
      next: () => this.select(detail.id),
      error: () => this.error.set('Could not change that member’s role.'),
    });
  }

  protected roleBadgeClass(role: WorkspaceRole): string {
    switch (role) {
      case 'OWNER':
        return 'bg-purple-100 text-purple-700';
      case 'ADMIN':
        return 'bg-blue-100 text-blue-700';
      default:
        return 'bg-slate-100 text-slate-600';
    }
  }

  protected initial(member: WorkspaceMember): string {
    return (member.fullName ?? member.username).charAt(0).toUpperCase();
  }

  private loadWorkspaces(): void {
    this.workspaceService.myWorkspaces().subscribe({
      next: (list) => this.workspaces.set(list),
      error: () => this.error.set('Could not load your workspaces.'),
    });
  }

  private inviteError(status: number): string {
    if (status === 404) {
      return 'No TeamFlow user was found with that email.';
    }
    if (status === 409) {
      return 'That user is already a member or has a pending invitation.';
    }
    if (status === 403) {
      return 'You do not have permission to invite members.';
    }
    return 'Could not send the invitation. Please try again.';
  }

  private reset(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
