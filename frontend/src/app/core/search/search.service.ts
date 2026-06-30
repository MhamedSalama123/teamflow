import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse, UserSearchParams, UserSearchResult } from './search.models';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly http = inject(HttpClient);

  /** Queries the user directory. Blank text filters are omitted so the backend treats them as absent. */
  search(params: UserSearchParams): Observable<PagedResponse<UserSearchResult>> {
    let httpParams = new HttpParams()
      .set('page', params.page ?? 0)
      .set('size', params.size ?? 10);
    if (params.q?.trim()) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.jobTitle?.trim()) {
      httpParams = httpParams.set('jobTitle', params.jobTitle.trim());
    }
    if (params.location?.trim()) {
      httpParams = httpParams.set('location', params.location.trim());
    }
    return this.http.get<PagedResponse<UserSearchResult>>('/api/users/search', {
      params: httpParams,
    });
  }
}
