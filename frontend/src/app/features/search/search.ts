import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { SearchService } from '../../core/search/search.service';
import { PagedResponse, UserSearchResult } from '../../core/search/search.models';

@Component({
  selector: 'app-search',
  imports: [ReactiveFormsModule],
  templateUrl: './search.html',
})
export class Search {
  private static readonly PAGE_SIZE = 10;

  private readonly fb = inject(FormBuilder);
  private readonly searchService = inject(SearchService);

  protected readonly results = signal<UserSearchResult[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly searched = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly filters = this.fb.nonNullable.group({
    q: [''],
    jobTitle: [''],
    location: [''],
  });

  protected submit(): void {
    this.load(0);
  }

  protected goTo(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.load(page);
    }
  }

  protected initial(result: UserSearchResult): string {
    return (result.fullName ?? result.username).charAt(0).toUpperCase();
  }

  private load(page: number): void {
    this.error.set(null);
    const { q, jobTitle, location } = this.filters.getRawValue();
    this.searchService
      .search({ q, jobTitle, location, page, size: Search.PAGE_SIZE })
      .subscribe({
        next: (res) => this.apply(res),
        error: () => this.error.set('Search failed. Please try again.'),
      });
  }

  private apply(res: PagedResponse<UserSearchResult>): void {
    this.results.set(res.content);
    this.page.set(res.page);
    this.totalPages.set(res.totalPages);
    this.totalElements.set(res.totalElements);
    this.searched.set(true);
  }
}
