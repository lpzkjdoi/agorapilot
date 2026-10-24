import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeFr from '@angular/common/locales/fr';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CurrentUser } from '../../../../core/auth/auth.model';
import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { ALL_PERMISSIONS } from '../../permissions';
import { Account, SecurityEvent } from '../../user.model';
import { UsersPageComponent } from './users-page.component';

registerLocaleData(localeFr, 'fr-FR');

const admin: Account = {
  id: 1, username: 'admin', status: 'ACTIVE', permissions: ALL_PERMISSIONS,
  lockedUntil: null, lastLoginAt: '2026-10-24T09:00:00', createdAt: null,
};
const camille: Account = {
  id: 2, username: 'camille', status: 'ACTIVE', permissions: ['publications:read', 'campaigns:read', 'medias:read'],
  lockedUntil: '2026-10-24T10:15:00', lastLoginAt: null, createdAt: null,
};
const bruno: Account = {
  id: 3, username: 'bruno', status: 'DISABLED', permissions: ['medias:read'],
  lockedUntil: null, lastLoginAt: null, createdAt: null,
};
const events: SecurityEvent[] = [
  { id: 2, occurredAt: '2026-10-24T09:05:00', type: 'LOGIN_FAILED', actor: null, target: 'inconnu', details: 'Pseudo inconnu' },
  { id: 1, occurredAt: '2026-10-24T09:00:00', type: 'LOGIN_SUCCEEDED', actor: 'admin', target: null, details: null },
];

describe('UsersPageComponent', () => {
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;
  const me = signal<CurrentUser | null>({ username: 'admin', status: 'ACTIVE', permissions: ALL_PERMISSIONS });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsersPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { user: me } }],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => httpTesting.verify());

  function render() {
    const fixture = TestBed.createComponent(UsersPageComponent);
    httpTesting.expectOne('/api/users').flush([admin, bruno, camille]);
    httpTesting.expectOne((request) => request.url === '/api/security-events').flush(events);
    fixture.detectChanges();
    return { fixture, compiled: fixture.nativeElement as HTMLElement };
  }

  function row(compiled: HTMLElement, username: string): HTMLElement {
    return Array.from(compiled.querySelectorAll<HTMLElement>('tbody tr'))
                .find((tr) => tr.querySelector('.users-name')?.textContent?.includes(username))!;
  }

  function button(scope: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(scope.querySelectorAll<HTMLButtonElement>('button'))
                .find((b) => b.textContent?.trim() === label)!;
  }

  function flushEvents(): void {
    httpTesting.expectOne((request) => request.url === '/api/security-events').flush(events);
  }

  it('should list the accounts with their status, profile and lock', () => {
    const { compiled } = render();

    expect(compiled.querySelectorAll('tbody tr').length).toBe(3);
    expect(row(compiled, 'camille').textContent).toContain('Lecteur');
    expect(row(compiled, 'camille').textContent).toContain('Verrouillé jusqu’à 10:15');
    expect(row(compiled, 'bruno').textContent).toContain('Désactivé');
    expect(row(compiled, 'admin').textContent).toContain('Administrateur');
  });

  it('should offer no action on the signed-in account', () => {
    const { compiled } = render();

    expect(row(compiled, 'admin').querySelector('.users-self')?.textContent).toBe('vous');
    expect(row(compiled, 'admin').querySelectorAll('button').length).toBe(0);
  });

  it('should offer to reactivate a disabled account rather than to disable it', () => {
    const { compiled } = render();

    expect(button(row(compiled, 'bruno'), 'Réactiver')).toBeTruthy();
    expect(button(row(compiled, 'bruno'), 'Désactiver')).toBeUndefined();
  });

  it('should show the security journal', () => {
    const { compiled } = render();
    const items = compiled.querySelectorAll('.users-event');

    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Connexion refusée');
    expect(items[0].textContent).toContain('inconnu');
    expect(items[0].classList).toContain('users-event-alert');
  });

  it('should create an account and reveal its temporary password once', () => {
    const { fixture, compiled } = render();

    button(compiled, 'Créer un compte').click();
    fixture.detectChanges();
    const input = compiled.querySelector('#account-username') as HTMLInputElement;
    input.value = 'zoe';
    input.dispatchEvent(new Event('input'));
    compiled.querySelector('app-account-modal form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const req = httpTesting.expectOne('/api/users');
    expect(req.request.body).toEqual({ username: 'zoe', permissions: [] });
    req.flush({ account: { ...bruno, id: 4, username: 'zoe', status: 'PENDING_PASSWORD', permissions: [] },
                temporaryPassword: 'abcd-efgh-jkmn-pqrs' });
    flushEvents();
    fixture.detectChanges();

    expect(compiled.querySelector('app-account-modal')).toBeNull();
    expect(compiled.querySelector('.temporary-value')?.textContent).toBe('abcd-efgh-jkmn-pqrs');
    expect(row(compiled, 'zoe').textContent).toContain('Mot de passe provisoire');
  });

  it('should keep the creation modal open with the reason of a refusal', () => {
    const { fixture, compiled } = render();

    button(compiled, 'Créer un compte').click();
    fixture.detectChanges();
    const input = compiled.querySelector('#account-username') as HTMLInputElement;
    input.value = 'camille';
    input.dispatchEvent(new Event('input'));
    compiled.querySelector('app-account-modal form')!.dispatchEvent(new Event('submit'));
    httpTesting.expectOne('/api/users').flush({ message: 'Le pseudo « camille » est déjà pris.' },
                                              { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(compiled.querySelector('app-account-modal [role="alert"]')?.textContent).toContain('déjà pris');
  });

  it('should save the permissions of an account', () => {
    const { fixture, compiled } = render();

    button(row(compiled, 'camille'), 'Droits').click();
    fixture.detectChanges();
    (compiled.querySelector('input[value="medias:write"]') as HTMLInputElement).click();
    fixture.detectChanges();
    compiled.querySelector('app-account-modal form')!.dispatchEvent(new Event('submit'));

    const req = httpTesting.expectOne('/api/users/2/permissions');
    expect(req.request.body).toEqual({ permissions: ['publications:read', 'campaigns:read', 'medias:read', 'medias:write'] });
    req.flush({ ...camille, permissions: req.request.body.permissions });
    flushEvents();
    fixture.detectChanges();

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'success', message: 'Droits de camille enregistrés.' }),
    ]);
    expect(row(compiled, 'camille').textContent).toContain('4 droits');
  });

  it('should ask for a confirmation before disabling', () => {
    const { fixture, compiled } = render();

    button(row(compiled, 'camille'), 'Désactiver').click();
    fixture.detectChanges();
    expect(row(compiled, 'camille').querySelector('.users-confirm')?.textContent).toContain('couper ses sessions');
    httpTesting.expectNone('/api/users/2/disable');

    button(row(compiled, 'camille'), 'Confirmer').click();
    httpTesting.expectOne('/api/users/2/disable').flush({ ...camille, status: 'DISABLED' });
    flushEvents();
    fixture.detectChanges();

    expect(row(compiled, 'camille').textContent).toContain('Désactivé');
  });

  it('should reactivate an account with a new temporary password', () => {
    const { fixture, compiled } = render();

    button(row(compiled, 'bruno'), 'Réactiver').click();
    fixture.detectChanges();
    button(row(compiled, 'bruno'), 'Confirmer').click();
    httpTesting.expectOne('/api/users/3/password-reset')
               .flush({ account: { ...bruno, status: 'PENDING_PASSWORD' }, temporaryPassword: 'wxyz-2345-6789-abcd' });
    flushEvents();
    fixture.detectChanges();

    expect(compiled.querySelector('.temporary-value')?.textContent).toBe('wxyz-2345-6789-abcd');
    expect(row(compiled, 'bruno').textContent).toContain('Mot de passe provisoire');
  });

  it('should notify a refused action', () => {
    const { fixture, compiled } = render();

    button(row(compiled, 'camille'), 'Désactiver').click();
    fixture.detectChanges();
    button(row(compiled, 'camille'), 'Confirmer').click();
    httpTesting.expectOne('/api/users/2/disable').flush({ message: 'Refusé.' }, { status: 403, statusText: 'Forbidden' });

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'error', message: 'Le compte n’a pas pu être désactivé. Refusé.' }),
    ]);
  });
});
