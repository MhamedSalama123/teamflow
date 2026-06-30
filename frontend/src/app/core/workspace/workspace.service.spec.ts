import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { WorkspaceService } from './workspace.service';
import { Workspace, WorkspaceDetail, WorkspaceMember } from './workspace.models';

const WORKSPACE: Workspace = {
  id: 1,
  name: 'Acme',
  role: 'OWNER',
  memberCount: 1,
  createdAt: '2026-06-30T00:00:00Z',
};

const MEMBER: WorkspaceMember = {
  userId: 2,
  username: 'bob',
  fullName: 'Bob Smith',
  email: 'bob@example.com',
  photoUrl: null,
  role: 'MEMBER',
  status: 'INVITED',
};

describe('WorkspaceService', () => {
  let service: WorkspaceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WorkspaceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('creates a workspace', () => {
    service.create('Acme').subscribe();

    const req = httpMock.expectOne('/api/workspaces');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Acme' });
    req.flush(WORKSPACE);
  });

  it('lists the current user workspaces', () => {
    service.myWorkspaces().subscribe();

    const req = httpMock.expectOne('/api/workspaces/me');
    expect(req.request.method).toBe('GET');
    req.flush([WORKSPACE]);
  });

  it('loads a workspace detail', () => {
    const detail: WorkspaceDetail = { id: 1, name: 'Acme', role: 'OWNER', members: [MEMBER] };
    service.detail(1).subscribe();

    const req = httpMock.expectOne('/api/workspaces/1');
    expect(req.request.method).toBe('GET');
    req.flush(detail);
  });

  it('invites a member by email', () => {
    service.invite(1, 'bob@example.com').subscribe();

    const req = httpMock.expectOne('/api/workspaces/1/invite');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'bob@example.com' });
    req.flush(MEMBER);
  });

  it('accepts and declines an invitation', () => {
    service.acceptInvite(1).subscribe();
    const accept = httpMock.expectOne('/api/workspaces/1/invite/accept');
    expect(accept.request.method).toBe('POST');
    accept.flush(WORKSPACE);

    service.declineInvite(1).subscribe();
    const decline = httpMock.expectOne('/api/workspaces/1/invite/decline');
    expect(decline.request.method).toBe('POST');
    decline.flush(null);
  });

  it('removes a member', () => {
    service.removeMember(1, 2).subscribe();

    const req = httpMock.expectOne('/api/workspaces/1/members/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('changes a member role', () => {
    service.changeRole(1, 2, 'ADMIN').subscribe();

    const req = httpMock.expectOne('/api/workspaces/1/members/2/role');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ role: 'ADMIN' });
    req.flush({ ...MEMBER, role: 'ADMIN', status: 'ACTIVE' });
  });
});
