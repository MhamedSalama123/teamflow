import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NotificationService } from '../../core/notification/notification.service';
import { AppNotification, NotificationType } from '../../core/notification/notification.models';

@Component({
  selector: 'app-notifications',
  imports: [DatePipe],
  templateUrl: './notifications.html',
})
export class Notifications implements OnInit {
  private readonly notificationService = inject(NotificationService);

  protected readonly notifications = this.notificationService.notifications;
  protected readonly unreadCount = this.notificationService.unreadCount;
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.notificationService.load().subscribe({
      error: () => this.error.set('Could not load notifications.'),
    });
  }

  protected markRead(notification: AppNotification): void {
    if (notification.read) {
      return;
    }
    this.notificationService.markRead(notification.id).subscribe({
      error: () => this.error.set('Could not update the notification.'),
    });
  }

  protected icon(type: NotificationType): string {
    return type === 'TASK_ASSIGNED' ? '✓' : '✉';
  }
}
