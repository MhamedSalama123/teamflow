import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Search } from './search';
import { SearchService } from '../../core/search/search.service';
import { PagedResponse, UserSearchResult } from '../../core/search/search.models';

function page(
  content: UserSearchResult[],
  overrides: Partial<PagedResponse<UserSearchResult>> = {},
): PagedResponse<UserSearchResult> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
    ...overrides,
  };
}

const ALICE: UserSearchResult = {
  id: 1,
  username: 'alice',
  fullName: 'Alice Wonder',
  photoUrl: null,
  jobTitle: 'Engineer',
  location: 'Cairo',
};

describe('Search', () => {
  let searchStub: { search: ReturnType<typeof vi.fn> };

  function setup() {
    TestBed.configureTestingModule({
      imports: [Search],
      providers: [{ provide: SearchService, useValue: searchStub }],
    });
    const fixture = TestBed.createComponent(Search);
    fixture.detectChanges();
    return fixture.componentInstance as any;
  }

  beforeEach(() => {
    searchStub = { search: vi.fn(() => of(page([ALICE]))) };
  });

  it('does not query until the user searches', () => {
    setup();
    expect(searchStub.search).not.toHaveBeenCalled();
  });

  it('searches from page 0 with the current filters', () => {
    const component = setup();
    component.filters.setValue({ q: 'ali', jobTitle: 'Engineer', location: '' });

    component.submit();

    expect(searchStub.search).toHaveBeenCalledWith({
      q: 'ali',
      jobTitle: 'Engineer',
      location: '',
      page: 0,
      size: 10,
    });
    expect(component.results()).toEqual([ALICE]);
    expect(component.searched()).toBe(true);
  });

  it('navigates between pages within bounds', () => {
    searchStub.search.mockReturnValue(of(page([ALICE], { page: 0, totalPages: 3 })));
    const component = setup();
    component.submit();

    component.goTo(1);
    expect(searchStub.search).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));

    searchStub.search.mockClear();
    component.goTo(3); // out of range -> ignored
    expect(searchStub.search).not.toHaveBeenCalled();
  });
});
