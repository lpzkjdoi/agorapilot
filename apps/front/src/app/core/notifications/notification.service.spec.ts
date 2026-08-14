import { TestBed } from '@angular/core/testing';
import {
  DEFAULT_NOTIFICATION_DURATION_MS,
  MAX_STACKED_NOTIFICATIONS,
  NOTIFICATION_EXIT_DURATION_MS,
} from './notification.model';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Fait passer l'auto-fermeture *et* l'animation de sortie. */
  function elapseFullLifetime(duration = DEFAULT_NOTIFICATION_DURATION_MS): void {
    vi.advanceTimersByTime(duration);
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
  }

  it('should start with an empty stack', () => {
    expect(service.notifications()).toEqual([]);
    expect(service.count()).toBe(0);
  });

  it('should stack a notification and return its id', () => {
    const id = service.sendNotification({ level: 'success', message: 'Publication créée.' });

    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0]).toEqual({
      id,
      level: 'success',
      message: 'Publication créée.',
      title: undefined,
      duration: DEFAULT_NOTIFICATION_DURATION_MS,
      dismissible: true,
      closing: false,
    });
  });

  it('should keep several notifications stacked, oldest first', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'error', message: 'Seconde' });

    expect(service.notifications().map((notification) => notification.message)).toEqual([
      'Première',
      'Seconde',
    ]);
    expect(service.count()).toBe(2);
  });

  it('should hand out a distinct id to each notification', () => {
    const first = service.sendNotification({ level: 'info', message: 'Première' });
    const second = service.sendNotification({ level: 'info', message: 'Seconde' });

    expect(second).not.toBe(first);
  });

  it('should close a notification after its display duration plus the exit animation', () => {
    service.sendNotification({ level: 'info', message: 'Éphémère' });

    vi.advanceTimersByTime(DEFAULT_NOTIFICATION_DURATION_MS);
    // Encore présente le temps de l'animation, mais marquée `closing`.
    expect(service.notifications()[0].closing).toBe(true);
    expect(service.count()).toBe(0);

    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    expect(service.notifications()).toEqual([]);
  });

  it('should honour a custom duration', () => {
    service.sendNotification({ level: 'info', message: 'Rapide', duration: 1_000 });

    vi.advanceTimersByTime(999);
    expect(service.notifications()[0].closing).toBe(false);

    elapseFullLifetime(1);
    expect(service.notifications()).toEqual([]);
  });

  it('should keep a notification with a zero duration until it is closed', () => {
    const id = service.sendNotification({ level: 'error', message: 'Bloquante', duration: 0 });

    vi.advanceTimersByTime(60_000);
    expect(service.notifications().length).toBe(1);

    service.closeNotification(id);
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    expect(service.notifications()).toEqual([]);
  });

  it('should close only the targeted notification', () => {
    const first = service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'info', message: 'Seconde' });

    service.closeNotification(first);
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);

    expect(service.notifications().map((notification) => notification.message)).toEqual(['Seconde']);
  });

  it('should ignore closing an unknown id', () => {
    service.sendNotification({ level: 'info', message: 'Présente' });

    service.closeNotification(4_242);
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);

    expect(service.notifications().length).toBe(1);
  });

  it('should ignore a second close on the same notification', () => {
    const id = service.sendNotification({ level: 'info', message: 'Présente' });

    service.closeNotification(id);
    vi.advanceTimersByTime(100);
    // Ne doit pas relancer une minuterie et retarder le retrait.
    service.closeNotification(id);
    vi.advanceTimersByTime(100);

    expect(service.notifications()).toEqual([]);
  });

  it('should close every notification with closeAll()', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'warning', message: 'Seconde' });
    service.sendNotification({ level: 'error', message: 'Troisième' });

    service.closeAll();
    expect(service.count()).toBe(0);

    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    expect(service.notifications()).toEqual([]);
  });

  it(`should close the oldest ones beyond ${MAX_STACKED_NOTIFICATIONS} stacked`, () => {
    for (let index = 1; index <= MAX_STACKED_NOTIFICATIONS + 1; index++) {
      service.sendNotification({ level: 'info', message: `Notification ${index}` });
    }

    expect(service.count()).toBe(MAX_STACKED_NOTIFICATIONS);
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);

    expect(service.notifications().map((notification) => notification.message)).toEqual([
      'Notification 2',
      'Notification 3',
      'Notification 4',
      'Notification 5',
    ]);
  });

  it('should carry the title and the dismissible flag', () => {
    service.sendNotification({
      level: 'warning',
      message: 'Aucun endpoint back.',
      title: 'Action indisponible',
      dismissible: false,
    });

    expect(service.notifications()[0].title).toBe('Action indisponible');
    expect(service.notifications()[0].dismissible).toBe(false);
  });

  it.each([
    ['success' as const],
    ['info' as const],
    ['warning' as const],
    ['error' as const],
  ])('should expose a %s() shortcut', (level) => {
    service[level]('Message', 'Titre');

    expect(service.notifications()[0].level).toBe(level);
    expect(service.notifications()[0].message).toBe('Message');
    expect(service.notifications()[0].title).toBe('Titre');
  });

  it('should drop its pending timers on destroy', () => {
    service.sendNotification({ level: 'info', message: 'Éphémère' });

    service.ngOnDestroy();
    elapseFullLifetime();

    // La minuterie a été annulée : la notification reste telle quelle.
    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].closing).toBe(false);
  });
});
