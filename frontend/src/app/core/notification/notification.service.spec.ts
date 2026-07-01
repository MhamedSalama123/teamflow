import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from './notification.service';
import { RealtimeService } from '../realtime/realtime.service';
import { AppNotification } from './notification.models';

const NOTIFICATIONS: AppNotification[] = [
  {
    id: 1,
    type: 'TASK_ASSIGNED',
    message: 'Ann assigned you the task X.',
    workspaceId: 2,
    read: false,
    createdAt: '2026-07-01T00:00:00Z',
  },
  {
    id: 2,
    type: 'WORKSPACE_INVITATION',
    message: 'You were invited.',
    workspaceId: 2,
    read: true,
    createdAt: '2026-06-30T00:00:00Z',
  },
];

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;
  let liveEvents: Subject<AppNotification>;

  beforeEach(() => {
    liveEvents = new Subject<AppNotification>();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: { watchNotifications: vi.fn(() => liveEvents) } },
      ],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads notifications and exposes the unread count', () => {
    service.load().subscribe();
    const req = httpMock.expectOne('/api/notifications/me');
    expect(req.request.method).toBe('GET');
    req.flush(NOTIFICATIONS);

    expect(service.notifications()).toHaveLength(2);
    expect(service.unreadCount()).toBe(1);
  });

  it('marks a notification read and decrements the unread count', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications/me').flush(NOTIFICATIONS);

    service.markRead(1).subscribe();
    const req = httpMock.expectOne('/api/notifications/1/read');
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(service.unreadCount()).toBe(0);
    expect(service.notifications().find((n) => n.id === 1)?.read).toBe(true);
  });

  it('prepends live-pushed notifications', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/notifications/me').flush(NOTIFICATIONS);
    service.startLiveUpdates();

    liveEvents.next({
      id: 9,
      type: 'TASK_ASSIGNED',
      message: 'New task for you.',
      workspaceId: 2,
      read: false,
      createdAt: '2026-07-02T00:00:00Z',
    });

    expect(service.notifications()[0].id).toBe(9);
    expect(service.unreadCount()).toBe(2);
  });
});
