import { Injectable, inject } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ChatMessage } from '../chat/chat.models';
import { AppNotification } from '../notification/notification.models';
import { TaskEvent } from '../project/project.models';

/**
 * Thin wrapper around a STOMP-over-WebSocket client. Each `watch*` call opens an authenticated
 * connection and streams messages from a destination; unsubscribing closes the connection.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);

  /** Task events broadcast to a project's board. */
  watchProject(projectId: number): Observable<TaskEvent> {
    return this.watch<TaskEvent>(`/topic/projects/${projectId}`);
  }

  /** New chat messages broadcast to a project's chat channel, via the dedicated `/ws/chat` endpoint. */
  watchProjectChat(projectId: number): Observable<ChatMessage> {
    return this.watch<ChatMessage>(`/topic/projects/${projectId}/chat`, '/ws/chat');
  }

  /** The current user's personal notification queue. */
  watchNotifications(): Observable<AppNotification> {
    return this.watch<AppNotification>('/user/queue/notifications');
  }

  private watch<T>(destination: string, endpoint = '/ws'): Observable<T> {
    return new Observable<T>((subscriber) => {
      const token = this.auth.getAccessToken();
      const client = new Client({
        brokerURL: this.brokerUrl(endpoint),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay: 5000,
      });

      client.onConnect = () => {
        client.subscribe(destination, (message) => {
          try {
            subscriber.next(JSON.parse(message.body) as T);
          } catch {
            // Ignore malformed frames.
          }
        });
      };

      client.activate();
      return () => void client.deactivate();
    });
  }

  private brokerUrl(endpoint: string): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${endpoint}`;
  }
}
