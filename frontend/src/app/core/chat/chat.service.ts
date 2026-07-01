import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatAttachment, ChatMessage, PagedResponse, SendMessageRequest } from './chat.models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  /** Paginated message history, newest first. */
  history(projectId: number, page = 0, size = 20): Observable<PagedResponse<ChatMessage>> {
    return this.http.get<PagedResponse<ChatMessage>>(
      `/api/projects/${projectId}/chat/messages?page=${page}&size=${size}`,
    );
  }

  sendMessage(projectId: number, request: SendMessageRequest): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`/api/projects/${projectId}/chat/messages`, request);
  }

  uploadAttachment(projectId: number, file: File): Observable<ChatAttachment> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ChatAttachment>(`/api/projects/${projectId}/chat/attachments`, form);
  }
}
