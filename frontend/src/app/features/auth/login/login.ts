import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const email = this.form.getRawValue().email;
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        if (err.status === 403) {
          // Account exists but the email isn't verified yet — send them to verify.
          this.router.navigate(['/verify-email'], { queryParams: { email } });
          return;
        }
        this.error.set(
          err.status === 401
            ? 'Invalid email or password.'
            : 'Login failed. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }

  protected signInWithGoogle(): void {
    this.auth.startGoogleLogin();
  }
}
