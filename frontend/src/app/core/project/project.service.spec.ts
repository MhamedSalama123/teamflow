import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ProjectService } from './project.service';
import { Project, Task } from './project.models';

const PROJECT: Project = { id: 1, name: 'Website', description: null, createdAt: '2026-07-01' };

const TASK: Task = {
  id: 5,
  title: 'Design',
  description: null,
  status: 'TODO',
  priority: 'MEDIUM',
  dueDate: null,
  assignee: null,
  position: 0,
};

describe('ProjectService', () => {
  let service: ProjectService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProjectService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists and creates projects', () => {
    service.listProjects(2).subscribe();
    const listReq = httpMock.expectOne('/api/workspaces/2/projects');
    expect(listReq.request.method).toBe('GET');
    listReq.flush([PROJECT]);

    service.createProject(2, { name: 'Website' }).subscribe();
    const createReq = httpMock.expectOne('/api/workspaces/2/projects');
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body).toEqual({ name: 'Website' });
    createReq.flush(PROJECT);
  });

  it('deletes a project', () => {
    service.deleteProject(2, 1).subscribe();
    const req = httpMock.expectOne('/api/workspaces/2/projects/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('lists and creates tasks', () => {
    service.listTasks(1).subscribe();
    const listReq = httpMock.expectOne('/api/projects/1/tasks');
    expect(listReq.request.method).toBe('GET');
    listReq.flush([TASK]);

    service.createTask(1, { title: 'Design', priority: 'HIGH' }).subscribe();
    const createReq = httpMock.expectOne('/api/projects/1/tasks');
    expect(createReq.request.body).toEqual({ title: 'Design', priority: 'HIGH' });
    createReq.flush(TASK);
  });

  it('updates a task (status move)', () => {
    service
      .updateTask(1, 5, {
        title: 'Design',
        status: 'IN_PROGRESS',
        priority: 'MEDIUM',
        position: null,
      })
      .subscribe();
    const req = httpMock.expectOne('/api/projects/1/tasks/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.status).toBe('IN_PROGRESS');
    req.flush({ ...TASK, status: 'IN_PROGRESS' });
  });

  it('assigns and deletes a task', () => {
    service.assignTask(1, 5, 9).subscribe();
    const assignReq = httpMock.expectOne('/api/projects/1/tasks/5/assign');
    expect(assignReq.request.method).toBe('PUT');
    expect(assignReq.request.body).toEqual({ assigneeId: 9 });
    assignReq.flush(TASK);

    service.deleteTask(1, 5).subscribe();
    const deleteReq = httpMock.expectOne('/api/projects/1/tasks/5');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null);
  });
});
