import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Profile } from './profile';
import { AuthService } from '../../core/auth/auth.service';
import { ProfileService } from '../../core/profile/profile.service';
import { UserProfile } from '../../core/profile/profile.models';

const PROFILE: UserProfile = {
  id: 1,
  email: 'a@b.com',
  username: 'alice',
  bio: 'Hello',
  jobTitle: 'Engineer',
  location: 'Cairo',
  phoneNumber: '+20',
  photoUrl: null,
  emailVerified: true,
};

describe('Profile', () => {
  let profileStub: {
    getProfile: ReturnType<typeof vi.fn>;
    updateProfile: ReturnType<typeof vi.fn>;
    changeEmail: ReturnType<typeof vi.fn>;
  };
  let authStub: { logout: ReturnType<typeof vi.fn> };

  function setup() {
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideRouter([]),
        { provide: ProfileService, useValue: profileStub },
        { provide: AuthService, useValue: authStub },
      ],
    });
    const fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    profileStub = {
      getProfile: vi.fn(() => of(PROFILE)),
      updateProfile: vi.fn(() => of(PROFILE)),
      changeEmail: vi.fn(() => of(PROFILE)),
    };
    authStub = { logout: vi.fn() };
  });

  it('loads the profile and populates the form', () => {
    const component = setup().componentInstance as any;
    expect(profileStub.getProfile).toHaveBeenCalled();
    expect(component.profileForm.getRawValue().jobTitle).toBe('Engineer');
  });

  it('saves the editable fields', () => {
    const component = setup().componentInstance as any;

    component.saveProfile();

    expect(profileStub.updateProfile).toHaveBeenCalledWith({
      bio: 'Hello',
      jobTitle: 'Engineer',
      location: 'Cairo',
      phoneNumber: '+20',
    });
    expect(component.profileSaved()).toBe(true);
  });

  it('signs out and routes to verification after an email change', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.emailForm.setValue({ newEmail: 'new@b.com' });
    component.changeEmail();

    expect(profileStub.changeEmail).toHaveBeenCalledWith('new@b.com');
    expect(authStub.logout).toHaveBeenCalled();
    expect(navSpy).toHaveBeenCalledWith(['/verify-email'], { queryParams: { email: 'new@b.com' } });
  });
});
