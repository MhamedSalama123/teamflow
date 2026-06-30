import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProfileService } from './profile.service';
import { UserProfile } from './profile.models';

const PROFILE: UserProfile = {
  id: 1,
  email: 'a@b.com',
  username: 'alice',
  bio: null,
  jobTitle: null,
  location: null,
  phoneNumber: null,
  photoUrl: null,
  emailVerified: true,
};

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('gets the current profile', () => {
    service.getProfile().subscribe();
    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('GET');
    req.flush(PROFILE);
  });

  it('updates the profile', () => {
    const body = { bio: 'hi', jobTitle: 'Eng', location: 'Cairo', phoneNumber: '+20' };
    service.updateProfile(body).subscribe();
    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush(PROFILE);
  });

  it('uploads a photo as multipart form data', () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    service.uploadPhoto(file).subscribe();
    const req = httpMock.expectOne('/api/users/me/photo');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({ photoUrl: '/uploads/x.png' });
  });

  it('changes the password', () => {
    service.changePassword('old', 'newpassword456').subscribe();
    const req = httpMock.expectOne('/api/users/me/password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ currentPassword: 'old', newPassword: 'newpassword456' });
    req.flush(null);
  });

  it('changes the email', () => {
    service.changeEmail('new@b.com').subscribe();
    const req = httpMock.expectOne('/api/users/me/email');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ newEmail: 'new@b.com' });
    req.flush(PROFILE);
  });

  it('deletes the account', () => {
    service.deleteAccount().subscribe();
    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
