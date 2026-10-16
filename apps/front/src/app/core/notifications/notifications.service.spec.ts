import { HttpErrorResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { NotificationsService } from './notifications.service';

describe('NotificationsService', () => {
  let service: NotificationsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NotificationsService);
  });

  it('should add a string message as info', fakeAsync(() => {
    service.send('Publication enregistrée');
    expect(service.notifications()).toEqual([
      { id: 0, level: 'info', message: 'Publication enregistrée' },
    ]);
    tick(5000);
  }));

  it('should convert an unreachable server error to an error notification', fakeAsync(() => {
    service.send(new HttpErrorResponse({ status: 0 }));
    expect(service.notifications()[0]).toEqual(
      jasmine.objectContaining({ level: 'error', message: 'Impossible de joindre le serveur.' }),
    );
    tick(5000);
  }));

  it('should use the API error message when present', fakeAsync(() => {
    service.send(new HttpErrorResponse({ status: 404, error: { message: 'Publication introuvable' } }));
    expect(service.notifications()[0].message).toBe('Publication introuvable');
    tick(5000);
  }));

  it('should fall back to the HTTP status when the API gives no message', fakeAsync(() => {
    service.send(new HttpErrorResponse({ status: 500 }));
    expect(service.notifications()[0].message).toBe('Erreur serveur (500).');
    tick(5000);
  }));

  it('should dismiss a notification manually', fakeAsync(() => {
    service.send('a');
    service.send('b');
    service.dismiss(0);
    expect(service.notifications().map((n) => n.message)).toEqual(['b']);
    tick(5000);
  }));

  it('should auto-dismiss after 5 seconds', fakeAsync(() => {
    service.send('a');
    tick(4999);
    expect(service.notifications().length).toBe(1);
    tick(1);
    expect(service.notifications().length).toBe(0);
  }));
});
