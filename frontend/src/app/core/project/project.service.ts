import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateProjectRequest,
  CreateTaskRequest,
  Project,
  Task,
  UpdateTaskRequest,
} from './project.models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);

  listProjects(workspaceId: number): Observable<Project[]> {
    return this.http.get<Project[]>(`/api/workspaces/${workspaceId}/projects`);
  }

  createProject(workspaceId: number, request: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(`/api/workspaces/${workspaceId}/projects`, request);
  }

  deleteProject(workspaceId: number, projectId: number): Observable<void> {
    return this.http.delete<void>(`/api/workspaces/${workspaceId}/projects/${projectId}`);
  }

  listTasks(projectId: number): Observable<Task[]> {
    return this.http.get<Task[]>(`/api/projects/${projectId}/tasks`);
  }

  createTask(projectId: number, request: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(`/api/projects/${projectId}/tasks`, request);
  }

  updateTask(projectId: number, taskId: number, request: UpdateTaskRequest): Observable<Task> {
    return this.http.put<Task>(`/api/projects/${projectId}/tasks/${taskId}`, request);
  }

  assignTask(projectId: number, taskId: number, assigneeId: number): Observable<Task> {
    return this.http.put<Task>(`/api/projects/${projectId}/tasks/${taskId}/assign`, { assigneeId });
  }

  deleteTask(projectId: number, taskId: number): Observable<void> {
    return this.http.delete<void>(`/api/projects/${projectId}/tasks/${taskId}`);
  }
}
