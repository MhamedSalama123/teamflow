import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ChatService } from './chat.service';
import { ChatMessage, PagedResponse } from './chat.models';

const MESSAGE: ChatMessage = {
  id: 1,
  content: 'Hello',
  attachmentUrl: null,
  attachmentName: null,
  sender: { id: 9, username: 'bob', fullName: 'Bob', photoUrl: null },
  createdAt: '2026-07-01T10:00:00Z',
};

describe('ChatService', () => {
  let service: ChatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads paginated history', () => {
    const page: PagedResponse<ChatMessage> = {
      content: [MESSAGE],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    service.history(1, 0, 20).subscribe();
    const req = httpMock.expectOne('/api/projects/1/chat/messages?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('sends a message', () => {
    service.sendMessage(1, { content: 'Hi @bob' }).subscribe();
    const req = httpMock.expectOne('/api/projects/1/chat/messages');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'Hi @bob' });
    req.flush(MESSAGE);
  });

  it('uploads an attachment as multipart form data', () => {
    const file = new File(['data'], 'notes.txt', { type: 'text/plain' });
    service.uploadAttachment(1, file).subscribe();
    const req = httpMock.expectOne('/api/projects/1/chat/attachments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({ url: '/uploads/x.txt', name: 'notes.txt' });
  });
});
