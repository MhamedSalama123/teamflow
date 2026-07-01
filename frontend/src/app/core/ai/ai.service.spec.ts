import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AiService } from './ai.service';

describe('AiService', () => {
  let service: AiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts a project id to summarize', () => {
    service.summarize(3).subscribe();
    const req = httpMock.expectOne('/api/ai/summarize');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ projectId: 3 });
    req.flush({ summary: 'done' });
  });

  it('posts a description to generate tasks', () => {
    service.generateTasks('build a page').subscribe();
    const req = httpMock.expectOne('/api/ai/generate-tasks');
    expect(req.request.body).toEqual({ description: 'build a page' });
    req.flush({ tasks: [] });
  });

  it('posts a project id and question to ask', () => {
    service.ask(3, 'what next?').subscribe();
    const req = httpMock.expectOne('/api/ai/ask');
    expect(req.request.body).toEqual({ projectId: 3, question: 'what next?' });
    req.flush({ answer: 'ship it' });
  });
});
