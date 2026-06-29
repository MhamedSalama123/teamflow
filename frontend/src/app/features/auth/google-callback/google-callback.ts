import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-google-callback',
  imports: [RouterLink],
  templateUrl: './google-callback.html',
})
export class GoogleCallback implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const code = params.get('code');

    if (params.get('error') || !code) {
      this.error.set('Google sign-in was cancelled or failed.');
      return;
    }

    this.auth.loginWithGoogle(code).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => this.error.set('Google sign-in failed. Please try again.'),
    });
  }
}
