import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subscription, tap } from 'rxjs';
import { RealtimeService } from '../realtime/realtime.service';
import { AppNotification } from './notification.models';

/**
 * Loads and tracks the current user's in-app notifications. The list is held in a signal so the nav
 * can show a live unread badge that updates as notifications are loaded, pushed, or marked read.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly realtime = inject(RealtimeService);
  private readonly baseUrl = '/api/notifications';

  private readonly _notifications = signal<AppNotification[]>([]);
  private liveSub: Subscription | null = null;

  readonly notifications = this._notifications.asReadonly();
  readonly unreadCount = computed(() => this._notifications().filter((n) => !n.read).length);

  /** Opens the live notification stream, prepending pushed notifications as they arrive. */
  startLiveUpdates(): void {
    if (this.liveSub) {
      return;
    }
    this.liveSub = this.realtime.watchNotifications().subscribe((notification) => {
      this._notifications.update((list) => [
        notification,
        ...list.filter((n) => n.id !== notification.id),
      ]);
    });
  }

  stopLiveUpdates(): void {
    this.liveSub?.unsubscribe();
    this.liveSub = null;
  }

  load(): Observable<AppNotification[]> {
    return this.http
      .get<AppNotification[]>(`${this.baseUrl}/me`)
      .pipe(tap((list) => this._notifications.set(list)));
  }

  markRead(id: number): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/${id}/read`, {})
      .pipe(
        tap(() =>
          this._notifications.update((list) =>
            list.map((n) => (n.id === id ? { ...n, read: true } : n)),
          ),
        ),
      );
  }
}
