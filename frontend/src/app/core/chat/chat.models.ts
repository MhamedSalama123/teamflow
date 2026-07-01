export interface ChatSender {
  id: number;
  username: string;
  fullName: string | null;
  photoUrl: string | null;
}

/** A chat message as returned by the API and broadcast over the chat topic. */
export interface ChatMessage {
  id: number;
  content: string | null;
  attachmentUrl: string | null;
  attachmentName: string | null;
  sender: ChatSender;
  createdAt: string;
}

export interface SendMessageRequest {
  content?: string | null;
  attachmentUrl?: string | null;
  attachmentName?: string | null;
}

/** The stored location of an uploaded attachment plus its original filename. */
export interface ChatAttachment {
  url: string;
  name: string;
}

/** Minimal pagination envelope mirroring the backend's PagedResponse. */
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
