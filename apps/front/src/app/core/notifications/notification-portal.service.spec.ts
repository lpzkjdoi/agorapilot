import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NotificationPortalService } from './notification-portal.service';
import { NotificationService } from './notification.service';

describe('NotificationPortalService', () => {
  let portal: NotificationPortalService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    portal = TestBed.inject(NotificationPortalService);
  });

  afterEach(() => {
    portal.detach();
  });

  function mountedContainers(): NodeListOf<Element> {
    return document.body.querySelectorAll(':scope > [aria-label="Notifications"]');
  }

  it('should mount the container directly in the body, outside the app tree', () => {
    portal.attach();

    expect(mountedContainers().length).toBe(1);
    expect(mountedContainers()[0].getAttribute('role')).toBe('region');
  });

  it('should mount the container only once', () => {
    portal.attach();
    portal.attach();

    expect(mountedContainers().length).toBe(1);
  });

  it('should render the notifications sent through the service', () => {
    portal.attach();

    TestBed.inject(NotificationService).sendNotification({
      level: 'warning',
      message: 'Cette action n’est pas encore disponible.',
    });
    TestBed.inject(ApplicationRef).tick();

    expect(document.body.querySelector('.toast-message')?.textContent?.trim()).toBe(
      'Cette action n’est pas encore disponible.',
    );
  });

  it('should remove the host element from the body on detach', () => {
    portal.attach();
    portal.detach();

    expect(mountedContainers().length).toBe(0);
  });

  it('should ignore a detach when nothing is mounted', () => {
    expect(() => portal.detach()).not.toThrow();
  });
});
