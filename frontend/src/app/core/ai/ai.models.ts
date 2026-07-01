import { TaskPriority } from '../project/project.models';

export interface SummaryResponse {
  summary: string;
}

/** A task suggested by the AI from a plain-text description. */
export interface GeneratedTask {
  title: string;
  priority: TaskPriority;
  dueDate: string | null;
}

export interface GenerateTasksResponse {
  tasks: GeneratedTask[];
}

export interface AnswerResponse {
  answer: string;
}
