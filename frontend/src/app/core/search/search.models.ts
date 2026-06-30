export interface UserSearchResult {
  id: number;
  username: string;
  fullName: string | null;
  photoUrl: string | null;
  jobTitle: string | null;
  location: string | null;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserSearchParams {
  q?: string;
  jobTitle?: string;
  location?: string;
  page?: number;
  size?: number;
}
