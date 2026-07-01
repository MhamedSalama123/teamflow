import { Component, effect, inject, input, signal } from '@angular/core';
import { AiService } from '../../core/ai/ai.service';
import { GeneratedTask } from '../../core/ai/ai.models';
import { ProjectService } from '../../core/project/project.service';

type AiTab = 'summarize' | 'generate' | 'ask';

/**
 * Per-project AI assistant with three tabs: summarize the chat, generate a task list from a
 * description (each suggestion can be added straight to the board), and ask a free-form question
 * about the project. Rendered as a child of the projects view.
 */
@Component({
  selector: 'app-ai-panel',
  templateUrl: './ai-panel.html',
})
export class AiPanel {
  private readonly aiService = inject(AiService);
  private readonly projectService = inject(ProjectService);

  readonly projectId = input.required<number>();

  protected readonly tabs: { id: AiTab; label: string }[] = [
    { id: 'summarize', label: 'Summarize' },
    { id: 'generate', label: 'Generate Tasks' },
    { id: 'ask', label: 'Ask' },
  ];

  protected readonly tab = signal<AiTab>('summarize');
  protected readonly error = signal<string | null>(null);

  protected readonly summary = signal<string | null>(null);
  protected readonly summarizing = signal(false);

  protected readonly description = signal('');
  protected readonly generating = signal(false);
  protected readonly generatedTasks = signal<GeneratedTask[]>([]);
  protected readonly addedTasks = signal<Set<number>>(new Set());

  protected readonly question = signal('');
  protected readonly asking = signal(false);
  protected readonly answer = signal<string | null>(null);

  constructor() {
    // Reset everything when the selected project changes.
    effect(() => {
      this.projectId();
      this.tab.set('summarize');
      this.error.set(null);
      this.summary.set(null);
      this.description.set('');
      this.generatedTasks.set([]);
      this.addedTasks.set(new Set());
      this.question.set('');
      this.answer.set(null);
    });
  }

  protected selectTab(tab: AiTab): void {
    this.tab.set(tab);
    this.error.set(null);
  }

  protected summarize(): void {
    this.error.set(null);
    this.summarizing.set(true);
    this.aiService.summarize(this.projectId()).subscribe({
      next: (response) => {
        this.summary.set(response.summary);
        this.summarizing.set(false);
      },
      error: () => {
        this.error.set('Could not summarize the chat.');
        this.summarizing.set(false);
      },
    });
  }

  protected generate(): void {
    const description = this.description().trim();
    if (!description) {
      return;
    }
    this.error.set(null);
    this.generating.set(true);
    this.addedTasks.set(new Set());
    this.aiService.generateTasks(description).subscribe({
      next: (response) => {
        this.generatedTasks.set(response.tasks);
        this.generating.set(false);
      },
      error: () => {
        this.error.set('Could not generate tasks.');
        this.generating.set(false);
      },
    });
  }

  protected addToBoard(task: GeneratedTask, index: number): void {
    this.error.set(null);
    this.projectService
      .createTask(this.projectId(), {
        title: task.title,
        priority: task.priority,
        dueDate: task.dueDate,
      })
      .subscribe({
        next: () => this.addedTasks.update((set) => new Set(set).add(index)),
        error: () => this.error.set('Could not add the task to the board.'),
      });
  }

  protected ask(): void {
    const question = this.question().trim();
    if (!question) {
      return;
    }
    this.error.set(null);
    this.asking.set(true);
    this.aiService.ask(this.projectId(), question).subscribe({
      next: (response) => {
        this.answer.set(response.answer);
        this.asking.set(false);
      },
      error: () => {
        this.error.set('Could not answer the question.');
        this.asking.set(false);
      },
    });
  }

  protected onInput(event: Event, target: 'description' | 'question'): void {
    const value = (event.target as HTMLTextAreaElement).value;
    if (target === 'description') {
      this.description.set(value);
    } else {
      this.question.set(value);
    }
  }
}
