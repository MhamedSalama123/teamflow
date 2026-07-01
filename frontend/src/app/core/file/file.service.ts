import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ProjectFile } from './file.models';

@Injectable({ providedIn: 'root' })
export class FileService {
  private readonly http = inject(HttpClient);

  list(projectId: number): Observable<ProjectFile[]> {
    return this.http.get<ProjectFile[]>(`/api/projects/${projectId}/files`);
  }

  upload(projectId: number, file: File): Observable<ProjectFile> {
    const form = new FormData();
    form.append('projectId', String(projectId));
    form.append('file', file);
    return this.http.post<ProjectFile>('/api/files/upload', form);
  }

  /** Fetches file bytes (auth header added by the interceptor) for download. */
  download(fileId: number): Observable<Blob> {
    return this.http.get(`/api/files/${fileId}/download`, { responseType: 'blob' });
  }

  /** Fetches image/PDF bytes for inline preview. */
  preview(fileId: number): Observable<Blob> {
    return this.http.get(`/api/files/${fileId}/preview`, { responseType: 'blob' });
  }
}
