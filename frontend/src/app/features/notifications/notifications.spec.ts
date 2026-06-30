import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Notifications } from './notifications';
import { NotificationService } from '../../core/notification/notification.service';
import { AppNotification } from '../../core/notification/notification.models';

const UNREAD: AppNotification = {
  id: 1,
  type: 'TASK_ASSIGNED',
  message: 'Ann assigned you the task X.',
  workspaceId: 2,
  read: false,
  createdAt: '2026-07-01T00:00:00Z',
};

describe('Notifications', () => {
  let serviceStub: {
    notifications: ReturnType<typeof signal<AppNotification[]>>;
    unreadCount: ReturnType<typeof signal<number>>;
    load: ReturnType<typeof vi.fn>;
    markRead: ReturnType<typeof vi.fn>;
  };

  function setup() {
    TestBed.configureTestingModule({
      imports: [Notifications],
      providers: [{ provide: NotificationService, useValue: serviceStub }],
    });
    const fixture = TestBed.createComponent(Notifications);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    serviceStub = {
      notifications: signal<AppNotification[]>([UNREAD]),
      unreadCount: signal(1),
      load: vi.fn(() => of([UNREAD])),
      markRead: vi.fn(() => of(undefined)),
    };
  });

  it('loads notifications on init', () => {
    setup();
    expect(serviceStub.load).toHaveBeenCalled();
  });

  it('marks an unread notification read', () => {
    const component = setup().componentInstance as any;
    component.markRead(UNREAD);
    expect(serviceStub.markRead).toHaveBeenCalledWith(1);
  });

  it('does not call the service for an already-read notification', () => {
    const component = setup().componentInstance as any;
    component.markRead({ ...UNREAD, read: true });
    expect(serviceStub.markRead).not.toHaveBeenCalled();
  });
});
