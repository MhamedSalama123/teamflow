import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

const TOKENS = { accessToken: 'tok', refreshToken: 'r', tokenType: 'Bearer' };

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  function authenticate(): void {
    auth.login({ email: 'a@b.com', password: 'password123' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(TOKENS);
  }

  it('attaches the bearer token to non-auth requests', () => {
    authenticate();

    http.get('/api/projects').subscribe();
    const req = httpMock.expectOne('/api/projects');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok');
    req.flush([]);
  });

  it('does not attach a token to auth requests', () => {
    auth.login({ email: 'a@b.com', password: 'password123' }).subscribe();
    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(TOKENS);
  });

  it('does not attach a header when there is no token', () => {
    http.get('/api/projects').subscribe();
    const req = httpMock.expectOne('/api/projects');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });
});
