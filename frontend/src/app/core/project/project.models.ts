export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface Project {
  id: number;
  name: string;
  description: string | null;
  createdAt: string;
}

export interface TaskAssignee {
  id: number;
  username: string;
  fullName: string | null;
  photoUrl: string | null;
}

export interface Task {
  id: number;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  assignee: TaskAssignee | null;
  position: number;
}

export interface CreateProjectRequest {
  name: string;
  description?: string | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string | null;
  priority?: TaskPriority;
  dueDate?: string | null;
  assigneeId?: number | null;
}

export interface UpdateTaskRequest {
  title: string;
  description?: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate?: string | null;
  assigneeId?: number | null;
  position?: number | null;
}
