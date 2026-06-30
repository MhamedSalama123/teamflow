import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ProfileService } from '../../core/profile/profile.service';
import { UserProfile } from '../../core/profile/profile.models';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule],
  templateUrl: './profile.html',
})
export class Profile implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly profileService = inject(ProfileService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly photoUrl = signal<string | null>(null);
  protected readonly previewUrl = signal<string | null>(null);
  private selectedFile: File | null = null;

  protected readonly profileSaved = signal(false);
  protected readonly profileError = signal<string | null>(null);
  protected readonly photoError = signal<string | null>(null);
  protected readonly passwordMessage = signal<string | null>(null);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly emailError = signal<string | null>(null);

  protected readonly profileForm = this.fb.nonNullable.group({
    fullName: [''],
    bio: [''],
    jobTitle: [''],
    location: [''],
    phoneNumber: [''],
  });

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected readonly emailForm = this.fb.nonNullable.group({
    newEmail: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.profileService.getProfile().subscribe({
      next: (profile) => this.applyProfile(profile),
      error: () => this.profileError.set('Could not load your profile.'),
    });
  }

  protected saveProfile(): void {
    this.profileSaved.set(false);
    this.profileError.set(null);
    this.profileService.updateProfile(this.profileForm.getRawValue()).subscribe({
      next: (profile) => {
        this.applyProfile(profile);
        this.profileSaved.set(true);
      },
      error: () => this.profileError.set('Could not save your profile.'),
    });
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile = file;
    this.previewUrl.set(file ? URL.createObjectURL(file) : null);
  }

  protected uploadPhoto(): void {
    if (!this.selectedFile) {
      return;
    }
    this.photoError.set(null);
    this.profileService.uploadPhoto(this.selectedFile).subscribe({
      next: (response) => {
        this.photoUrl.set(response.photoUrl);
        this.previewUrl.set(null);
        this.selectedFile = null;
      },
      error: () => this.photoError.set('Could not upload the photo. Use an image under 5 MB.'),
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.passwordMessage.set(null);
    this.passwordError.set(null);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.profileService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordMessage.set('Password updated.');
        this.passwordForm.reset();
      },
      error: (err) =>
        this.passwordError.set(
          err.status === 400
            ? 'Your current password is incorrect.'
            : 'Could not change your password.',
        ),
    });
  }

  protected changeEmail(): void {
    if (this.emailForm.invalid) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.emailError.set(null);
    const newEmail = this.emailForm.getRawValue().newEmail;
    this.profileService.changeEmail(newEmail).subscribe({
      next: () => {
        // Changing the email invalidates the current session, so sign out and verify the new one.
        this.auth.logout();
        this.router.navigate(['/verify-email'], { queryParams: { email: newEmail } });
      },
      error: (err) =>
        this.emailError.set(
          err.status === 409
            ? 'That email is already in use.'
            : 'Could not change your email.',
        ),
    });
  }

  protected deleteAccount(): void {
    if (!confirm('Delete your account? This cannot be undone.')) {
      return;
    }
    this.profileService.deleteAccount().subscribe({
      next: () => {
        this.auth.logout();
        this.router.navigateByUrl('/register');
      },
      error: () => this.profileError.set('Could not delete your account.'),
    });
  }

  private applyProfile(profile: UserProfile): void {
    this.profile.set(profile);
    this.photoUrl.set(profile.photoUrl);
    this.profileForm.patchValue({
      fullName: profile.fullName ?? '',
      bio: profile.bio ?? '',
      jobTitle: profile.jobTitle ?? '',
      location: profile.location ?? '',
      phoneNumber: profile.phoneNumber ?? '',
    });
  }
}
