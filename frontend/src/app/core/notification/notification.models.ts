export type NotificationType = 'WORKSPACE_INVITATION' | 'TASK_ASSIGNED';

export interface AppNotification {
  id: number;
  type: NotificationType;
  message: string;
  workspaceId: number | null;
  read: boolean;
  createdAt: string;
}
