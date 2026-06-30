import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UpdateProfileRequest, UserProfile } from './profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/users/me';

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(this.baseUrl);
  }

  updateProfile(request: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>(this.baseUrl, request);
  }

  uploadPhoto(file: File): Observable<{ photoUrl: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ photoUrl: string }>(`${this.baseUrl}/photo`, form);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/password`, { currentPassword, newPassword });
  }

  changeEmail(newEmail: string): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.baseUrl}/email`, { newEmail });
  }

  deleteAccount(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
