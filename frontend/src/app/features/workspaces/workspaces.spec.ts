import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Workspaces } from './workspaces';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Workspace, WorkspaceDetail } from '../../core/workspace/workspace.models';

const WORKSPACE: Workspace = {
  id: 1,
  name: 'Acme',
  role: 'OWNER',
  memberCount: 2,
  createdAt: '2026-06-30T00:00:00Z',
};

const DETAIL: WorkspaceDetail = {
  id: 1,
  name: 'Acme',
  role: 'OWNER',
  members: [
    {
      userId: 1,
      username: 'ann',
      fullName: 'Ann Owner',
      email: 'ann@example.com',
      photoUrl: null,
      role: 'OWNER',
      status: 'ACTIVE',
    },
    {
      userId: 2,
      username: 'bob',
      fullName: 'Bob Smith',
      email: 'bob@example.com',
      photoUrl: null,
      role: 'MEMBER',
      status: 'ACTIVE',
    },
  ],
  pendingInvitations: [{ id: 9, email: 'newcomer@example.com', role: 'MEMBER' }],
};

describe('Workspaces', () => {
  let serviceStub: {
    myWorkspaces: ReturnType<typeof vi.fn>;
    detail: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    invite: ReturnType<typeof vi.fn>;
    removeMember: ReturnType<typeof vi.fn>;
    changeRole: ReturnType<typeof vi.fn>;
    cancelInvitation: ReturnType<typeof vi.fn>;
  };

  function setup() {
    TestBed.configureTestingModule({
      imports: [Workspaces],
      providers: [{ provide: WorkspaceService, useValue: serviceStub }],
    });
    const fixture = TestBed.createComponent(Workspaces);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    serviceStub = {
      myWorkspaces: vi.fn(() => of([WORKSPACE])),
      detail: vi.fn(() => of(DETAIL)),
      create: vi.fn(() => of(WORKSPACE)),
      invite: vi.fn(() => of(DETAIL.members[1])),
      removeMember: vi.fn(() => of(undefined)),
      changeRole: vi.fn(() => of(DETAIL.members[1])),
      cancelInvitation: vi.fn(() => of(undefined)),
    };
  });

  it('loads the current user workspaces on init', () => {
    const component = setup().componentInstance as any;
    expect(serviceStub.myWorkspaces).toHaveBeenCalled();
    expect(component.workspaces()).toHaveLength(1);
  });

  it('creates a workspace and selects it', () => {
    const component = setup().componentInstance as any;
    component.createForm.setValue({ name: 'New Team' });

    component.create();

    expect(serviceStub.create).toHaveBeenCalledWith('New Team');
    expect(serviceStub.detail).toHaveBeenCalledWith(1);
    expect(component.selected()?.id).toBe(1);
  });

  it('does not create when the name is blank', () => {
    const component = setup().componentInstance as any;
    component.createForm.setValue({ name: '' });

    component.create();

    expect(serviceStub.create).not.toHaveBeenCalled();
  });

  it('invites a member and refreshes the selected workspace', () => {
    const component = setup().componentInstance as any;
    component.select(1);
    component.inviteForm.setValue({ email: 'carol@example.com', role: 'MEMBER' });

    component.invite();

    expect(serviceStub.invite).toHaveBeenCalledWith(1, 'carol@example.com', 'MEMBER');
    expect(component.notice()).toBe('Invitation sent.');
  });

  it('invites directly as admin when chosen', () => {
    const component = setup().componentInstance as any;
    component.select(1);
    component.inviteForm.setValue({ email: 'carol@example.com', role: 'ADMIN' });

    component.invite();

    expect(serviceStub.invite).toHaveBeenCalledWith(1, 'carol@example.com', 'ADMIN');
  });

  it('allows granting admin only to an owner', () => {
    const component = setup().componentInstance as any;
    component.select(1);
    expect(component.canInviteAsAdmin()).toBe(true);

    serviceStub.detail.mockReturnValueOnce(of({ ...DETAIL, role: 'ADMIN' }));
    component.select(1);
    expect(component.canInviteAsAdmin()).toBe(false);
  });

  it('exposes management ability based on the current role', () => {
    const component = setup().componentInstance as any;
    component.select(1);
    expect(component.canManage()).toBe(true);

    serviceStub.detail.mockReturnValueOnce(of({ ...DETAIL, role: 'MEMBER' }));
    component.select(1);
    expect(component.canManage()).toBe(false);
  });

  it('changes a member role through the service', () => {
    const component = setup().componentInstance as any;
    component.select(1);

    component.changeRole(DETAIL.members[1], 'ADMIN');

    expect(serviceStub.changeRole).toHaveBeenCalledWith(1, 2, 'ADMIN');
  });

  it('cancels a pending invitation through the service', () => {
    const component = setup().componentInstance as any;
    component.select(1);

    component.cancelInvitation(DETAIL.pendingInvitations[0]);

    expect(serviceStub.cancelInvitation).toHaveBeenCalledWith(1, 9);
  });
});
