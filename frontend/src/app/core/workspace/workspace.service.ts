import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Workspace, WorkspaceDetail, WorkspaceMember, WorkspaceRole } from './workspace.models';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/workspaces';

  create(name: string): Observable<Workspace> {
    return this.http.post<Workspace>(this.baseUrl, { name });
  }

  myWorkspaces(): Observable<Workspace[]> {
    return this.http.get<Workspace[]>(`${this.baseUrl}/me`);
  }

  detail(workspaceId: number): Observable<WorkspaceDetail> {
    return this.http.get<WorkspaceDetail>(`${this.baseUrl}/${workspaceId}`);
  }

  invite(workspaceId: number, email: string): Observable<WorkspaceMember> {
    return this.http.post<WorkspaceMember>(`${this.baseUrl}/${workspaceId}/invite`, { email });
  }

  acceptInvite(workspaceId: number): Observable<Workspace> {
    return this.http.post<Workspace>(`${this.baseUrl}/${workspaceId}/invite/accept`, {});
  }

  declineInvite(workspaceId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${workspaceId}/invite/decline`, {});
  }

  removeMember(workspaceId: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${workspaceId}/members/${userId}`);
  }

  changeRole(
    workspaceId: number,
    userId: number,
    role: WorkspaceRole,
  ): Observable<WorkspaceMember> {
    return this.http.put<WorkspaceMember>(`${this.baseUrl}/${workspaceId}/members/${userId}/role`, {
      role,
    });
  }
}
