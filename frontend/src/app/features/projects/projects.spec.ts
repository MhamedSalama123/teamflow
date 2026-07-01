import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Projects } from './projects';
import { ChatService } from '../../core/chat/chat.service';
import { FileService } from '../../core/file/file.service';
import { ProjectService } from '../../core/project/project.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Project, Task, TaskEvent } from '../../core/project/project.models';
import { Workspace, WorkspaceDetail } from '../../core/workspace/workspace.models';

const WORKSPACES: Workspace[] = [
  { id: 2, name: 'Acme', role: 'OWNER', memberCount: 1, createdAt: '2026-07-01' },
];

const PROJECTS: Project[] = [
  { id: 1, name: 'Website', description: null, createdAt: '2026-07-01' },
];

const DETAIL: WorkspaceDetail = {
  id: 2,
  name: 'Acme',
  role: 'OWNER',
  members: [
    {
      userId: 9,
      username: 'bob',
      fullName: 'Bob Smith',
      email: 'bob@example.com',
      photoUrl: null,
      role: 'MEMBER',
      status: 'ACTIVE',
    },
    {
      userId: 10,
      username: 'pending',
      fullName: null,
      email: 'pending@example.com',
      photoUrl: null,
      role: 'MEMBER',
      status: 'INVITED',
    },
  ],
  pendingInvitations: [],
};

const TASKS: Task[] = [
  {
    id: 5,
    title: 'Design',
    description: null,
    status: 'TODO',
    priority: 'MEDIUM',
    dueDate: null,
    assignee: null,
    position: 0,
  },
  {
    id: 6,
    title: 'Build',
    description: null,
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    dueDate: null,
    assignee: null,
    position: 0,
  },
];

type Fn = ReturnType<typeof vi.fn>;

describe('Projects', () => {
  let projectStub: {
    listProjects: Fn;
    createProject: Fn;
    deleteProject: Fn;
    listTasks: Fn;
    createTask: Fn;
    updateTask: Fn;
    assignTask: Fn;
    deleteTask: Fn;
  };
  let workspaceStub: { myWorkspaces: Fn; detail: Fn };
  let realtimeEvents: Subject<TaskEvent>;
  let realtimeStub: { watchProject: Fn; watchProjectChat: Fn };
  let chatStub: { history: Fn; sendMessage: Fn; uploadAttachment: Fn };
  let fileStub: { list: Fn; upload: Fn; download: Fn; preview: Fn };

  function setup() {
    TestBed.configureTestingModule({
      imports: [Projects],
      providers: [
        { provide: ProjectService, useValue: projectStub },
        { provide: WorkspaceService, useValue: workspaceStub },
        { provide: RealtimeService, useValue: realtimeStub },
        { provide: ChatService, useValue: chatStub },
        { provide: FileService, useValue: fileStub },
      ],
    });
    const fixture = TestBed.createComponent(Projects);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    realtimeEvents = new Subject<TaskEvent>();
    realtimeStub = {
      watchProject: vi.fn(() => realtimeEvents.asObservable()),
      watchProjectChat: vi.fn(() => new Subject().asObservable()),
    };
    chatStub = {
      history: vi.fn(() =>
        of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      ),
      sendMessage: vi.fn(() => of(undefined)),
      uploadAttachment: vi.fn(() => of(undefined)),
    };
    fileStub = {
      list: vi.fn(() => of([])),
      upload: vi.fn(() => of(undefined)),
      download: vi.fn(() => of(new Blob())),
      preview: vi.fn(() => of(new Blob())),
    };
    projectStub = {
      listProjects: vi.fn(() => of(PROJECTS)),
      createProject: vi.fn(() => of(PROJECTS[0])),
      deleteProject: vi.fn(() => of(undefined)),
      listTasks: vi.fn(() => of(TASKS)),
      createTask: vi.fn(() => of(TASKS[0])),
      updateTask: vi.fn(() => of(TASKS[0])),
      assignTask: vi.fn(() => of(TASKS[0])),
      deleteTask: vi.fn(() => of(undefined)),
    };
    workspaceStub = {
      myWorkspaces: vi.fn(() => of(WORKSPACES)),
      detail: vi.fn(() => of(DETAIL)),
    };
  });

  it('loads the first workspace, its projects, tasks and active members', () => {
    const component = setup().componentInstance as any;
    expect(component.selectedWorkspaceId()).toBe(2);
    expect(component.projects()).toHaveLength(1);
    expect(component.selectedProjectId()).toBe(1);
    expect(component.tasks()).toHaveLength(2);
    // The pending (INVITED) member is filtered out of the assignee list.
    expect(component.members()).toHaveLength(1);
    expect(component.canManageProjects()).toBe(true);
  });

  it('subscribes to realtime task events and refreshes on a message', () => {
    const component = setup().componentInstance as any;
    expect(realtimeStub.watchProject).toHaveBeenCalledWith(1);
    expect(projectStub.listTasks).toHaveBeenCalledTimes(1);

    realtimeEvents.next({ type: 'UPDATED', taskId: 5 });

    expect(projectStub.listTasks).toHaveBeenCalledTimes(2);
  });

  it('groups tasks into the right columns', () => {
    const component = setup().componentInstance as any;
    expect(component.tasksFor('TODO').map((t: Task) => t.id)).toEqual([5]);
    expect(component.tasksFor('IN_PROGRESS').map((t: Task) => t.id)).toEqual([6]);
    expect(component.tasksFor('DONE')).toHaveLength(0);
  });

  it('creates a task with mapped optional fields', () => {
    const component = setup().componentInstance as any;
    component.taskForm.setValue({ title: 'New', priority: 'HIGH', dueDate: '', assigneeId: '' });

    component.createTask();

    expect(projectStub.createTask).toHaveBeenCalledWith(1, {
      title: 'New',
      priority: 'HIGH',
      dueDate: null,
      assigneeId: null,
    });
  });

  it('moves a task to another column on drop', () => {
    const component = setup().componentInstance as any;
    component.onDragStart(TASKS[0]);

    component.onDrop('DONE');

    expect(projectStub.updateTask).toHaveBeenCalledWith(
      1,
      5,
      expect.objectContaining({ status: 'DONE', title: 'Design' }),
    );
  });

  it('does not move when dropped on the same column', () => {
    const component = setup().componentInstance as any;
    component.onDragStart(TASKS[0]);

    component.onDrop('TODO');

    expect(projectStub.updateTask).not.toHaveBeenCalled();
  });

  it('edits a task, preserving status and assignee', () => {
    const component = setup().componentInstance as any;
    const task: Task = {
      ...TASKS[1],
      assignee: { id: 9, username: 'bob', fullName: 'Bob', photoUrl: null },
    };

    component.startEdit(task);
    expect(component.editingTaskId()).toBe(task.id);
    expect(component.editForm.getRawValue().title).toBe('Build');

    component.editForm.patchValue({
      title: 'Build v2',
      description: 'Updated',
      priority: 'LOW',
      dueDate: '2026-09-01',
    });
    component.saveEdit(task);

    expect(projectStub.updateTask).toHaveBeenCalledWith(1, 6, {
      title: 'Build v2',
      description: 'Updated',
      status: 'IN_PROGRESS',
      priority: 'LOW',
      dueDate: '2026-09-01',
      assigneeId: 9,
      position: null,
    });
    expect(component.editingTaskId()).toBeNull();
  });

  it('assigns through the assign endpoint and unassigns through update', () => {
    const component = setup().componentInstance as any;

    component.onAssigneeChange(TASKS[0], '9');
    expect(projectStub.assignTask).toHaveBeenCalledWith(1, 5, 9);

    component.onAssigneeChange(TASKS[0], '');
    expect(projectStub.updateTask).toHaveBeenCalledWith(
      1,
      5,
      expect.objectContaining({ assigneeId: null }),
    );
  });
});
