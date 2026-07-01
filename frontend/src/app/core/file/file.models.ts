export interface FileUploader {
  id: number;
  username: string;
  fullName: string | null;
}

/** A file uploaded to a project, as returned by the API. */
export interface ProjectFile {
  id: number;
  name: string;
  contentType: string;
  size: number;
  downloadUrl: string;
  previewUrl: string | null;
  previewable: boolean;
  uploadedBy: FileUploader;
  createdAt: string;
}
