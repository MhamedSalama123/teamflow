import { DatePipe } from '@angular/common';
import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin } from 'rxjs';
import { FileService } from '../../core/file/file.service';
import { ProjectFile } from '../../core/file/file.models';

interface PreviewState {
  name: string;
  kind: 'image' | 'pdf';
  url: string;
  safeUrl: SafeResourceUrl;
}

/**
 * File management for a single project: a drag-and-drop upload zone, a list of uploaded files with
 * download buttons, and inline preview for images and PDFs. Rendered as a child of the projects view.
 * Downloads and previews fetch the bytes over HTTP (so the JWT interceptor authenticates them) and
 * render them from an object URL rather than pointing at the endpoint directly.
 */
@Component({
  selector: 'app-files-panel',
  imports: [DatePipe],
  templateUrl: './files-panel.html',
})
export class FilesPanel implements OnDestroy {
  private readonly fileService = inject(FileService);
  private readonly sanitizer = inject(DomSanitizer);

  /** The project whose files are shown. */
  readonly projectId = input.required<number>();

  protected readonly files = signal<ProjectFile[]>([]);
  protected readonly uploading = signal(false);
  protected readonly dragOver = signal(false);
  protected readonly preview = signal<PreviewState | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.projectId();
      this.closePreview();
      this.files.set([]);
      this.error.set(null);
      this.loadFiles(id);
    });
  }

  ngOnDestroy(): void {
    this.closePreview();
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    if (event.dataTransfer?.files) {
      this.handleFiles(event.dataTransfer.files);
    }
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.handleFiles(input.files);
    }
    input.value = '';
  }

  protected download(file: ProjectFile): void {
    this.error.set(null);
    this.fileService.download(file.id).subscribe({
      next: (blob) => this.saveBlob(blob, file.name),
      error: () => this.error.set('Could not download the file.'),
    });
  }

  protected openPreview(file: ProjectFile): void {
    this.error.set(null);
    this.fileService.preview(file.id).subscribe({
      next: (blob) => {
        this.closePreview();
        const url = URL.createObjectURL(blob);
        const kind: 'image' | 'pdf' = file.contentType === 'application/pdf' ? 'pdf' : 'image';
        this.preview.set({
          name: file.name,
          kind,
          url,
          safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(url),
        });
      },
      error: () => this.error.set('Could not load the preview.'),
    });
  }

  protected closePreview(): void {
    const current = this.preview();
    if (current) {
      URL.revokeObjectURL(current.url);
      this.preview.set(null);
    }
  }

  protected formatSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  protected uploaderName(file: ProjectFile): string {
    return file.uploadedBy.fullName ?? file.uploadedBy.username;
  }

  private handleFiles(fileList: FileList): void {
    const files = Array.from(fileList);
    if (files.length === 0) {
      return;
    }
    const projectId = this.projectId();
    this.error.set(null);
    this.uploading.set(true);
    forkJoin(files.map((file) => this.fileService.upload(projectId, file))).subscribe({
      next: () => {
        this.uploading.set(false);
        this.loadFiles(projectId);
      },
      error: () => {
        this.uploading.set(false);
        this.error.set('Could not upload one or more files.');
        this.loadFiles(projectId);
      },
    });
  }

  private loadFiles(projectId: number): void {
    this.fileService.list(projectId).subscribe({
      next: (files) => {
        if (this.projectId() === projectId) {
          this.files.set(files);
        }
      },
      error: () => this.error.set('Could not load files.'),
    });
  }

  private saveBlob(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
