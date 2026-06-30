import { Injectable, inject } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { TaskEvent } from '../project/project.models';

/**
 * Thin wrapper around a STOMP-over-WebSocket client. {@link watchProject} opens an authenticated
 * connection and streams the task events broadcast to that project's topic; unsubscribing closes it.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);

  watchProject(projectId: number): Observable<TaskEvent> {
    return new Observable<TaskEvent>((subscriber) => {
      const token = this.auth.getAccessToken();
      const client = new Client({
        brokerURL: this.brokerUrl(),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay: 5000,
      });

      client.onConnect = () => {
        client.subscribe(`/topic/projects/${projectId}`, (message) => {
          try {
            subscriber.next(JSON.parse(message.body) as TaskEvent);
          } catch {
            // Ignore malformed frames.
          }
        });
      };

      client.activate();
      return () => void client.deactivate();
    });
  }

  private brokerUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }
}
