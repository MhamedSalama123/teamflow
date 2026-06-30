import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SearchService } from './search.service';
import { PagedResponse, UserSearchResult } from './search.models';

const EMPTY_PAGE: PagedResponse<UserSearchResult> = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
};

describe('SearchService', () => {
  let service: SearchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SearchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends paging params and omits blank filters', () => {
    service.search({ q: '  ', jobTitle: 'Engineer', location: '', page: 2, size: 10 }).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/users/search' && r.params.get('jobTitle') === 'Engineer',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('location')).toBe(false);
    req.flush(EMPTY_PAGE);
  });

  it('trims and forwards the query text', () => {
    service.search({ q: '  ann  ' }).subscribe();

    const req = httpMock.expectOne((r) => r.params.get('q') === 'ann');
    expect(req.request.params.get('q')).toBe('ann');
    req.flush(EMPTY_PAGE);
  });
});
