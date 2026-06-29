import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

const RESEND_COOLDOWN_SECONDS = 60;

@Component({
  selector: 'app-verify-email',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verify-email.html',
})
export class VerifyEmail implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly email = signal('');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly cooldown = signal(0);

  private intervalId?: ReturnType<typeof setInterval>;

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  ngOnInit(): void {
    this.email.set(this.route.snapshot.queryParamMap.get('email') ?? '');
  }

  ngOnDestroy(): void {
    this.stopCooldown();
  }

  protected submit(): void {
    if (this.form.invalid || !this.email()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.verifyEmail({ email: this.email(), code: this.form.getRawValue().code }).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(
          err.status === 429
            ? 'Too many incorrect attempts. Request a new code in a few minutes.'
            : 'Invalid or expired code. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }

  protected resend(): void {
    if (this.cooldown() > 0 || !this.email()) {
      return;
    }
    this.error.set(null);
    this.auth.resendVerification(this.email()).subscribe({
      next: () => this.startCooldown(),
      error: () => this.error.set('Could not resend the code. Please try again shortly.'),
    });
  }

  private startCooldown(): void {
    this.cooldown.set(RESEND_COOLDOWN_SECONDS);
    this.intervalId = setInterval(() => {
      const next = this.cooldown() - 1;
      this.cooldown.set(next);
      if (next <= 0) {
        this.stopCooldown();
      }
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = undefined;
    }
  }
}
