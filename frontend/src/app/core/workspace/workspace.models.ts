export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export type WorkspaceMemberStatus = 'INVITED' | 'ACTIVE';

export interface Workspace {
  id: number;
  name: string;
  role: WorkspaceRole;
  memberCount: number;
  createdAt: string;
}

export interface WorkspaceMember {
  userId: number;
  username: string;
  fullName: string | null;
  email: string;
  photoUrl: string | null;
  role: WorkspaceRole;
  status: WorkspaceMemberStatus;
}

export interface WorkspaceDetail {
  id: number;
  name: string;
  role: WorkspaceRole;
  members: WorkspaceMember[];
}
