import { Component, effect, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/notification/notification.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly notificationService = inject(NotificationService);

  protected readonly unreadCount = this.notificationService.unreadCount;
  private loaded = false;

  constructor() {
    // Once authenticated, load existing notifications and open the live stream so the nav badge
    // reflects the unread count and updates in real time.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        if (!this.loaded) {
          this.loaded = true;
          this.notificationService.load().subscribe({ error: () => {} });
          this.notificationService.startLiveUpdates();
        }
      } else if (this.loaded) {
        this.loaded = false;
        this.notificationService.stopLiveUpdates();
      }
    });
  }
}
