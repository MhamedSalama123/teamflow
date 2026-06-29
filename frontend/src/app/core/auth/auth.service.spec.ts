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

  it('posts to register and stores the returned tokens', () => {
    const body = { email: 'a@b.com', username: 'alice', password: 'password123' };
    service.register(body).subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(TOKENS);

    expect(service.getAccessToken()).toBe('access-token');
    expect(service.isAuthenticated()).toBe(true);
    expect(localStorage.getItem('teamflow.accessToken')).toBe('access-token');
    expect(localStorage.getItem('teamflow.refreshToken')).toBe('refresh-token');
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
});
