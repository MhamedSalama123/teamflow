import { DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { ChatService } from '../../core/chat/chat.service';
import { ChatAttachment, ChatMessage } from '../../core/chat/chat.models';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { WorkspaceMember } from '../../core/workspace/workspace.models';

/** Location of the `@mention` token currently being typed. */
interface MentionContext {
  start: number;
  query: string;
}

/**
 * Real-time chat for a single project. Loads recent history, streams new messages over STOMP, and
 * supports `@mention` autocomplete plus file attachments. Rendered as a child of the projects view.
 */
@Component({
  selector: 'app-chat-panel',
  imports: [DatePipe],
  templateUrl: './chat-panel.html',
})
export class ChatPanel {
  private readonly chatService = inject(ChatService);
  private readonly realtime = inject(RealtimeService);

  /** The project whose chat channel is shown. */
  readonly projectId = input.required<number>();
  /** Active workspace members, used to power `@mention` autocomplete. */
  readonly members = input<WorkspaceMember[]>([]);

  private readonly box = viewChild<ElementRef<HTMLTextAreaElement>>('box');
  private readonly log = viewChild<ElementRef<HTMLElement>>('log');

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly pendingAttachment = signal<ChatAttachment | null>(null);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly mention = signal<MentionContext | null>(null);
  protected readonly mentionSuggestions = computed<WorkspaceMember[]>(() => {
    const context = this.mention();
    if (context === null) {
      return [];
    }
    const query = context.query.toLowerCase();
    return this.members()
      .filter(
        (m) =>
          m.username.toLowerCase().includes(query) ||
          (m.fullName?.toLowerCase().includes(query) ?? false),
      )
      .slice(0, 5);
  });

  constructor() {
    // Reload history and re-subscribe whenever the selected project changes.
    effect((onCleanup) => {
      const id = this.projectId();
      this.messages.set([]);
      this.draft.set('');
      this.pendingAttachment.set(null);
      this.mention.set(null);
      this.error.set(null);
      this.loadHistory(id);
      const sub = this.realtime.watchProjectChat(id).subscribe((message) => {
        this.appendMessage(message);
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  protected onInput(event: Event): void {
    const el = event.target as HTMLTextAreaElement;
    this.draft.set(el.value);
    this.updateMention(el.value, el.selectionStart ?? el.value.length);
  }

  protected onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (keyboard.shiftKey) {
      return;
    }
    event.preventDefault();
    this.send();
  }

  protected selectMention(member: WorkspaceMember): void {
    const context = this.mention();
    if (context === null) {
      return;
    }
    const text = this.draft();
    const caret = context.start + context.query.length + 1;
    const before = text.slice(0, context.start);
    const after = text.slice(caret);
    const inserted = `@${member.username} `;
    const newText = before + inserted + after;
    this.draft.set(newText);
    this.mention.set(null);
    const el = this.box()?.nativeElement;
    if (el) {
      const position = before.length + inserted.length;
      el.value = newText;
      el.focus();
      el.setSelectionRange(position, position);
    }
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.error.set(null);
    this.uploading.set(true);
    this.chatService.uploadAttachment(this.projectId(), file).subscribe({
      next: (attachment) => {
        this.pendingAttachment.set(attachment);
        this.uploading.set(false);
      },
      error: () => {
        this.error.set('Could not upload the file.');
        this.uploading.set(false);
      },
    });
    input.value = '';
  }

  protected clearAttachment(): void {
    this.pendingAttachment.set(null);
  }

  protected send(): void {
    const content = this.draft().trim();
    const attachment = this.pendingAttachment();
    if (!content && !attachment) {
      return;
    }
    this.error.set(null);
    this.chatService
      .sendMessage(this.projectId(), {
        content: content || null,
        attachmentUrl: attachment?.url ?? null,
        attachmentName: attachment?.name ?? null,
      })
      .subscribe({
        next: (message) => {
          this.appendMessage(message);
          this.draft.set('');
          this.pendingAttachment.set(null);
          this.mention.set(null);
        },
        error: () => this.error.set('Could not send the message.'),
      });
  }

  protected senderName(message: ChatMessage): string {
    return message.sender.fullName ?? message.sender.username;
  }

  protected isImage(url: string): boolean {
    return /\.(png|jpe?g|gif|webp)$/i.test(url);
  }

  private updateMention(text: string, caret: number): void {
    // Walk back from the caret to the start of the current word; it's a mention if it begins with @.
    let start = caret;
    while (start > 0 && /[\w@]/.test(text[start - 1])) {
      start--;
    }
    const token = text.slice(start, caret);
    if (token.startsWith('@') && !token.slice(1).includes('@')) {
      this.mention.set({ start, query: token.slice(1) });
    } else {
      this.mention.set(null);
    }
  }

  private loadHistory(projectId: number): void {
    this.chatService.history(projectId, 0, 50).subscribe({
      next: (page) => {
        if (this.projectId() === projectId) {
          // The API returns newest first; display oldest at the top.
          this.messages.set([...page.content].reverse());
          this.scrollToBottom();
        }
      },
      error: () => this.error.set('Could not load messages.'),
    });
  }

  private appendMessage(message: ChatMessage): void {
    this.messages.update((current) =>
      current.some((m) => m.id === message.id) ? current : [...current, message],
    );
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    const el = this.log()?.nativeElement;
    if (el) {
      queueMicrotask(() => (el.scrollTop = el.scrollHeight));
    }
  }
}
