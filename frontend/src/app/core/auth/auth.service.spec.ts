import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse } from './auth.models';

const TOKENS: AuthResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts unauthenticated', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.getAccessToken()).toBeNull();
  });

  it('posts to register but does not authenticate until the email is verified', () => {
    const body = { email: 'a@b.com', username: 'alice', password: 'password123' };
    let response: { email: string } | undefined;
    service.register(body).subscribe((r) => (response = r));

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ email: 'a@b.com', message: 'A verification code has been sent to your email.' });

    expect(response?.email).toBe('a@b.com');
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('teamflow.accessToken')).toBeNull();
  });

  it('verifyEmail posts the code and stores the returned tokens', () => {
    service.verifyEmail({ email: 'a@b.com', code: '123456' }).subscribe();

    const req = httpMock.expectOne('/api/auth/verify-email');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com', code: '123456' });
    req.flush(TOKENS);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getAccessToken()).toBe('access-token');
  });

  it('resendVerification posts the email', () => {
    service.resendVerification('a@b.com').subscribe();

    const req = httpMock.expectOne('/api/auth/resend-verification');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com' });
    req.flush(null);
  });

  it('posts to login and stores the returned tokens', () => {
    service.login({ email: 'a@b.com', password: 'password123' }).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(TOKENS);

    expect(service.isAuthenticated()).toBe(true);
  });

  it('clears tokens on logout', () => {
    service.login({ email: 'a@b.com', password: 'password123' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(TOKENS);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('teamflow.accessToken')).toBeNull();
  });

  it('exchanges a google authorization code and stores the returned tokens', () => {
    service.loginWithGoogle('auth-code').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/auth/google/callback');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('code')).toBe('auth-code');
    req.flush(TOKENS);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getAccessToken()).toBe('access-token');
  });
});
