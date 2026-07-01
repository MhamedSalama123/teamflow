import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FileService } from './file.service';
import { ProjectFile } from './file.models';

const FILE: ProjectFile = {
  id: 7,
  name: 'spec.pdf',
  contentType: 'application/pdf',
  size: 2048,
  downloadUrl: '/api/files/7/download',
  previewUrl: '/api/files/7/preview',
  previewable: true,
  uploadedBy: { id: 1, username: 'alice', fullName: 'Alice' },
  createdAt: '2026-07-01T10:00:00Z',
};

describe('FileService', () => {
  let service: FileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists a project files', () => {
    service.list(3).subscribe();
    const req = httpMock.expectOne('/api/projects/3/files');
    expect(req.request.method).toBe('GET');
    req.flush([FILE]);
  });

  it('uploads a file as multipart with the project id', () => {
    const file = new File(['data'], 'spec.pdf', { type: 'application/pdf' });
    service.upload(3, file).subscribe();
    const req = httpMock.expectOne('/api/files/upload');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body instanceof FormData).toBe(true);
    expect(body.get('projectId')).toBe('3');
    req.flush(FILE);
  });

  it('downloads and previews as blobs', () => {
    service.download(7).subscribe();
    const dl = httpMock.expectOne('/api/files/7/download');
    expect(dl.request.method).toBe('GET');
    expect(dl.request.responseType).toBe('blob');
    dl.flush(new Blob(['x']));

    service.preview(7).subscribe();
    const pv = httpMock.expectOne('/api/files/7/preview');
    expect(pv.request.responseType).toBe('blob');
    pv.flush(new Blob(['x']));
  });
});
