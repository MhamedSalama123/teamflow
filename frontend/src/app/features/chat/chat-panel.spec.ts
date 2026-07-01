import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatPanel } from './chat-panel';
import { ChatService } from '../../core/chat/chat.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { ChatMessage } from '../../core/chat/chat.models';
import { WorkspaceMember } from '../../core/workspace/workspace.models';

const MEMBERS: WorkspaceMember[] = [
  {
    userId: 9,
    username: 'bob',
    fullName: 'Bob Smith',
    email: 'bob@example.com',
    photoUrl: null,
    role: 'MEMBER',
    status: 'ACTIVE',
  },
];

function message(id: number, content: string): ChatMessage {
  return {
    id,
    content,
    attachmentUrl: null,
    attachmentName: null,
    sender: { id: 9, username: 'bob', fullName: 'Bob Smith', photoUrl: null },
    createdAt: '2026-07-01T10:00:00Z',
  };
}

type Fn = ReturnType<typeof vi.fn>;

describe('ChatPanel', () => {
  let chatStub: { history: Fn; sendMessage: Fn; uploadAttachment: Fn };
  let chatStream: Subject<ChatMessage>;
  let realtimeStub: { watchProjectChat: Fn };

  function setup() {
    TestBed.configureTestingModule({
      imports: [ChatPanel],
      providers: [
        { provide: ChatService, useValue: chatStub },
        { provide: RealtimeService, useValue: realtimeStub },
      ],
    });
    const fixture = TestBed.createComponent(ChatPanel);
    fixture.componentRef.setInput('projectId', 1);
    fixture.componentRef.setInput('members', MEMBERS);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    chatStream = new Subject<ChatMessage>();
    realtimeStub = { watchProjectChat: vi.fn(() => chatStream.asObservable()) };
    chatStub = {
      history: vi.fn(() =>
        of({
          content: [message(2, 'Newer'), message(1, 'Older')],
          page: 0,
          size: 50,
          totalElements: 2,
          totalPages: 1,
        }),
      ),
      sendMessage: vi.fn((_projectId: number, req) =>
        of(message(3, req.content ?? '')),
      ),
      uploadAttachment: vi.fn(() => of({ url: '/uploads/x.txt', name: 'x.txt' })),
    };
  });

  it('loads history oldest-first and subscribes to the chat stream', () => {
    const component = setup().componentInstance as any;
    expect(realtimeStub.watchProjectChat).toHaveBeenCalledWith(1);
    expect(component.messages().map((m: ChatMessage) => m.id)).toEqual([1, 2]);
  });

  it('appends live messages and ignores duplicates', () => {
    const component = setup().componentInstance as any;
    chatStream.next(message(3, 'Live'));
    expect(component.messages().map((m: ChatMessage) => m.id)).toEqual([1, 2, 3]);
    chatStream.next(message(3, 'Live'));
    expect(component.messages()).toHaveLength(3);
  });

  it('offers mention suggestions and inserts the selected member', () => {
    const component = setup().componentInstance as any;
    component.onInput({ target: { value: 'hi @b', selectionStart: 5 } });
    expect(component.mentionSuggestions().map((m: WorkspaceMember) => m.username)).toEqual(['bob']);

    component.selectMention(MEMBERS[0]);
    expect(component.draft()).toBe('hi @bob ');
    expect(component.mentionSuggestions()).toHaveLength(0);
  });

  it('sends a message and clears the draft', () => {
    const component = setup().componentInstance as any;
    component.draft.set('Hello @bob');

    component.send();

    expect(chatStub.sendMessage).toHaveBeenCalledWith(1, {
      content: 'Hello @bob',
      attachmentUrl: null,
      attachmentName: null,
    });
    expect(component.draft()).toBe('');
    expect(component.messages().map((m: ChatMessage) => m.id)).toContain(3);
  });

  it('does not send an empty message', () => {
    const component = setup().componentInstance as any;
    component.draft.set('   ');
    component.send();
    expect(chatStub.sendMessage).not.toHaveBeenCalled();
  });
});
