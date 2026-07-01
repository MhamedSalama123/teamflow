import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ProjectService } from '../../core/project/project.service';
import { Project, Task, TaskPriority, TaskStatus } from '../../core/project/project.models';
import { ChatPanel } from '../chat/chat-panel';
import { FilesPanel } from '../files/files-panel';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Workspace, WorkspaceMember, WorkspaceRole } from '../../core/workspace/workspace.models';

interface Column {
  status: TaskStatus;
  label: string;
}

const MANAGED_ROLES: WorkspaceRole[] = ['OWNER', 'ADMIN'];

@Component({
  selector: 'app-projects',
  imports: [ReactiveFormsModule, ChatPanel, FilesPanel],
  templateUrl: './projects.html',
})
export class Projects implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly workspaceService = inject(WorkspaceService);
  private readonly realtime = inject(RealtimeService);

  /** Live subscription to the selected project's task events. */
  private realtimeSub: Subscription | null = null;

  protected readonly columns: Column[] = [
    { status: 'TODO', label: 'To Do' },
    { status: 'IN_PROGRESS', label: 'In Progress' },
    { status: 'DONE', label: 'Done' },
  ];
  protected readonly priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

  protected readonly workspaces = signal<Workspace[]>([]);
  protected readonly selectedWorkspaceId = signal<number | null>(null);
  protected readonly projects = signal<Project[]>([]);
  protected readonly selectedProjectId = signal<number | null>(null);
  protected readonly members = signal<WorkspaceMember[]>([]);
  protected readonly tasks = signal<Task[]>([]);
  protected readonly error = signal<string | null>(null);
  private readonly draggedTaskId = signal<number | null>(null);

  protected readonly selectedWorkspace = computed(
    () => this.workspaces().find((w) => w.id === this.selectedWorkspaceId()) ?? null,
  );
  protected readonly canManageProjects = computed(() => {
    const role = this.selectedWorkspace()?.role;
    return role !== undefined && MANAGED_ROLES.includes(role);
  });

  protected readonly projectForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
  });

  protected readonly taskForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    priority: ['MEDIUM' as TaskPriority],
    dueDate: [''],
    assigneeId: [''],
  });

  protected readonly editingTaskId = signal<number | null>(null);
  protected readonly editForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', [Validators.maxLength(5000)]],
    priority: ['MEDIUM' as TaskPriority],
    dueDate: [''],
  });

  ngOnInit(): void {
    this.workspaceService.myWorkspaces().subscribe({
      next: (list) => {
        this.workspaces.set(list);
        if (list.length > 0) {
          this.selectWorkspace(list[0].id);
        }
      },
      error: () => this.error.set('Could not load your workspaces.'),
    });
  }

  ngOnDestroy(): void {
    this.stopRealtime();
  }

  protected selectWorkspace(workspaceId: number): void {
    this.error.set(null);
    this.stopRealtime();
    this.selectedWorkspaceId.set(workspaceId);
    this.selectedProjectId.set(null);
    this.tasks.set([]);
    this.projects.set([]);
    this.members.set([]);
    this.projectService.listProjects(workspaceId).subscribe({
      next: (projects) => {
        this.projects.set(projects);
        if (projects.length > 0) {
          this.selectProject(projects[0].id);
        }
      },
      error: () => this.error.set('Could not load projects.'),
    });
    this.workspaceService.detail(workspaceId).subscribe({
      next: (detail) => this.members.set(detail.members.filter((m) => m.status === 'ACTIVE')),
      error: () => this.members.set([]),
    });
  }

  protected selectProject(projectId: number): void {
    this.error.set(null);
    this.selectedProjectId.set(projectId);
    this.loadTasks(projectId);
    this.startRealtime(projectId);
  }

  /** Subscribes to live task events for the project, refreshing the board when they arrive. */
  private startRealtime(projectId: number): void {
    this.stopRealtime();
    this.realtimeSub = this.realtime.watchProject(projectId).subscribe(() => {
      if (this.selectedProjectId() === projectId) {
        this.loadTasks(projectId);
      }
    });
  }

  private stopRealtime(): void {
    this.realtimeSub?.unsubscribe();
    this.realtimeSub = null;
  }

  protected createProject(): void {
    const workspaceId = this.selectedWorkspaceId();
    if (workspaceId === null || this.projectForm.invalid) {
      return;
    }
    this.error.set(null);
    this.projectService.createProject(workspaceId, this.projectForm.getRawValue()).subscribe({
      next: (project) => {
        this.projectForm.reset({ name: '' });
        this.projectService.listProjects(workspaceId).subscribe((list) => this.projects.set(list));
        this.selectProject(project.id);
      },
      error: () => this.error.set('Could not create the project.'),
    });
  }

  protected deleteProject(project: Project): void {
    const workspaceId = this.selectedWorkspaceId();
    if (workspaceId === null) {
      return;
    }
    this.error.set(null);
    this.projectService.deleteProject(workspaceId, project.id).subscribe({
      next: () => {
        if (this.selectedProjectId() === project.id) {
          this.selectedProjectId.set(null);
          this.tasks.set([]);
        }
        this.projectService.listProjects(workspaceId).subscribe((list) => this.projects.set(list));
      },
      error: () => this.error.set('Could not delete the project.'),
    });
  }

  protected createTask(): void {
    const projectId = this.selectedProjectId();
    if (projectId === null || this.taskForm.invalid) {
      return;
    }
    this.error.set(null);
    const { title, priority, dueDate, assigneeId } = this.taskForm.getRawValue();
    this.projectService
      .createTask(projectId, {
        title,
        priority,
        dueDate: dueDate || null,
        assigneeId: assigneeId ? Number(assigneeId) : null,
      })
      .subscribe({
        next: () => {
          this.taskForm.reset({ title: '', priority: 'MEDIUM', dueDate: '', assigneeId: '' });
          this.loadTasks(projectId);
        },
        error: () => this.error.set('Could not create the task.'),
      });
  }

  protected deleteTask(task: Task): void {
    const projectId = this.selectedProjectId();
    if (projectId === null) {
      return;
    }
    this.error.set(null);
    this.projectService.deleteTask(projectId, task.id).subscribe({
      next: () => this.loadTasks(projectId),
      error: () => this.error.set('Could not delete the task.'),
    });
  }

  protected startEdit(task: Task): void {
    this.editingTaskId.set(task.id);
    this.editForm.setValue({
      title: task.title,
      description: task.description ?? '',
      priority: task.priority,
      dueDate: task.dueDate ?? '',
    });
  }

  protected cancelEdit(): void {
    this.editingTaskId.set(null);
  }

  protected saveEdit(task: Task): void {
    const projectId = this.selectedProjectId();
    if (projectId === null || this.editForm.invalid) {
      return;
    }
    this.error.set(null);
    const { title, description, priority, dueDate } = this.editForm.getRawValue();
    this.projectService
      .updateTask(projectId, task.id, {
        title,
        description: description || null,
        status: task.status,
        priority,
        dueDate: dueDate || null,
        assigneeId: task.assignee?.id ?? null,
        position: null,
      })
      .subscribe({
        next: () => {
          this.editingTaskId.set(null);
          this.loadTasks(projectId);
        },
        error: () => this.error.set('Could not update the task.'),
      });
  }

  protected onAssigneeChange(task: Task, value: string): void {
    const projectId = this.selectedProjectId();
    if (projectId === null) {
      return;
    }
    this.error.set(null);
    const done = {
      next: () => this.loadTasks(projectId),
      error: () => this.error.set('Could not update the assignee.'),
    };
    if (value) {
      this.projectService.assignTask(projectId, task.id, Number(value)).subscribe(done);
    } else {
      this.projectService
        .updateTask(projectId, task.id, this.toUpdate(task, { assigneeId: null }))
        .subscribe(done);
    }
  }

  protected tasksFor(status: TaskStatus): Task[] {
    return this.tasks()
      .filter((t) => t.status === status)
      .sort((a, b) => a.position - b.position);
  }

  // --- Native drag & drop ---

  protected onDragStart(task: Task): void {
    this.draggedTaskId.set(task.id);
  }

  protected onDrop(status: TaskStatus): void {
    const projectId = this.selectedProjectId();
    const taskId = this.draggedTaskId();
    this.draggedTaskId.set(null);
    if (projectId === null || taskId === null) {
      return;
    }
    const task = this.tasks().find((t) => t.id === taskId);
    if (!task || task.status === status) {
      return;
    }
    this.error.set(null);
    // Append to the end of the target column (position omitted lets the server place it last).
    this.projectService.updateTask(projectId, taskId, this.toUpdate(task, { status })).subscribe({
      next: () => this.loadTasks(projectId),
      error: () => this.error.set('Could not move the task.'),
    });
  }

  protected priorityClass(priority: TaskPriority): string {
    switch (priority) {
      case 'HIGH':
        return 'bg-red-100 text-red-700';
      case 'MEDIUM':
        return 'bg-amber-100 text-amber-700';
      default:
        return 'bg-slate-100 text-slate-600';
    }
  }

  protected assigneeLabel(member: WorkspaceMember): string {
    return member.fullName ?? member.username;
  }

  private toUpdate(
    task: Task,
    overrides: Partial<{ status: TaskStatus; assigneeId: number | null }>,
  ) {
    return {
      title: task.title,
      description: task.description,
      status: overrides.status ?? task.status,
      priority: task.priority,
      dueDate: task.dueDate,
      assigneeId: 'assigneeId' in overrides ? overrides.assigneeId! : (task.assignee?.id ?? null),
      position: null,
    };
  }

  private loadTasks(projectId: number): void {
    this.projectService.listTasks(projectId).subscribe({
      next: (tasks) => this.tasks.set(tasks),
      error: () => this.error.set('Could not load tasks.'),
    });
  }
}
