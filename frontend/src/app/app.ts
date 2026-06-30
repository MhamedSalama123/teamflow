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
    // Load notifications once the user is authenticated so the nav badge reflects the unread count.
    effect(() => {
      if (this.auth.isAuthenticated() && !this.loaded) {
        this.loaded = true;
        this.notificationService.load().subscribe({ error: () => {} });
      }
    });
  }
}
