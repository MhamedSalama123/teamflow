import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnswerResponse, GenerateTasksResponse, SummaryResponse } from './ai.models';

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);

  summarize(projectId: number): Observable<SummaryResponse> {
    return this.http.post<SummaryResponse>('/api/ai/summarize', { projectId });
  }

  generateTasks(description: string): Observable<GenerateTasksResponse> {
    return this.http.post<GenerateTasksResponse>('/api/ai/generate-tasks', { description });
  }

  ask(projectId: number, question: string): Observable<AnswerResponse> {
    return this.http.post<AnswerResponse>('/api/ai/ask', { projectId, question });
  }
}
