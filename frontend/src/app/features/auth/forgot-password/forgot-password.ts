import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    // The confirmation is shown regardless of outcome so the page never reveals whether an
    // account exists for the address.
    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.markSubmitted(),
      error: () => this.markSubmitted(),
    });
  }

  private markSubmitted(): void {
    this.submitted.set(true);
    this.submitting.set(false);
  }
}
