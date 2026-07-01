import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FilesPanel } from './files-panel';
import { FileService } from '../../core/file/file.service';
import { ProjectFile } from '../../core/file/file.models';

const FILE: ProjectFile = {
  id: 7,
  name: 'diagram.png',
  contentType: 'image/png',
  size: 2048,
  downloadUrl: '/api/files/7/download',
  previewUrl: '/api/files/7/preview',
  previewable: true,
  uploadedBy: { id: 1, username: 'alice', fullName: 'Alice' },
  createdAt: '2026-07-01T10:00:00Z',
};

type Fn = ReturnType<typeof vi.fn>;

describe('FilesPanel', () => {
  let fileStub: { list: Fn; upload: Fn; download: Fn; preview: Fn };

  function setup() {
    TestBed.configureTestingModule({
      imports: [FilesPanel],
      providers: [{ provide: FileService, useValue: fileStub }],
    });
    const fixture = TestBed.createComponent(FilesPanel);
    fixture.componentRef.setInput('projectId', 1);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:mock');
    URL.revokeObjectURL = vi.fn();
    fileStub = {
      list: vi.fn(() => of([FILE])),
      upload: vi.fn(() => of(FILE)),
      download: vi.fn(() => of(new Blob(['x']))),
      preview: vi.fn(() => of(new Blob(['x']))),
    };
  });

  it('loads the project files on init', () => {
    const component = setup().componentInstance as any;
    expect(fileStub.list).toHaveBeenCalledWith(1);
    expect(component.files().map((f: ProjectFile) => f.id)).toEqual([7]);
  });

  it('uploads dropped files then reloads the list', () => {
    const component = setup().componentInstance as any;
    const dropped = new File(['data'], 'new.pdf', { type: 'application/pdf' });
    component.onDrop({ preventDefault() {}, dataTransfer: { files: [dropped] } });

    expect(fileStub.upload).toHaveBeenCalledWith(1, dropped);
    // list called once on init, once after upload
    expect(fileStub.list).toHaveBeenCalledTimes(2);
    expect(component.uploading()).toBe(false);
  });

  it('downloads a file through the service', () => {
    const component = setup().componentInstance as any;
    component.download(FILE);
    expect(fileStub.download).toHaveBeenCalledWith(7);
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('opens and closes an inline preview', () => {
    const component = setup().componentInstance as any;
    component.openPreview(FILE);
    expect(fileStub.preview).toHaveBeenCalledWith(7);
    expect(component.preview()?.kind).toBe('image');

    component.closePreview();
    expect(component.preview()).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });
});
