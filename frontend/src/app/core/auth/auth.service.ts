import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from './auth.models';
import { buildGoogleAuthUrl } from './google-oauth.config';

const ACCESS_TOKEN_KEY = 'teamflow.accessToken';
const REFRESH_TOKEN_KEY = 'teamflow.refreshToken';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/auth';

  private readonly accessToken = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));

  /** Reactive flag the UI can read to switch between authenticated/anonymous views. */
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, request)
      .pipe(tap((response) => this.storeTokens(response)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, request)
      .pipe(tap((response) => this.storeTokens(response)));
  }

  /** Redirects the browser to Google's consent screen to begin the OAuth2 flow. */
  startGoogleLogin(): void {
    window.location.href = buildGoogleAuthUrl();
  }

  /** Exchanges the authorization code returned by Google for our own JWT pair. */
  loginWithGoogle(code: string): Observable<AuthResponse> {
    return this.http
      .get<AuthResponse>(`${this.baseUrl}/google/callback`, { params: { code } })
      .pipe(tap((response) => this.storeTokens(response)));
  }

  logout(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.accessToken.set(null);
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  private storeTokens(response: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    this.accessToken.set(response.accessToken);
  }
}
