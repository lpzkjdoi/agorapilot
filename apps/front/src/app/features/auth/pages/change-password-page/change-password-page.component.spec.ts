import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { CurrentUser } from '../../../../core/auth/auth.model';
import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { ChangePasswordPageComponent } from './change-password-page.component';

describe('ChangePasswordPageComponent', () => {
  const user = signal<CurrentUser | null>(null);
  let changePassword: ReturnType<typeof vi.fn<(request: unknown) => Observable<CurrentUser>>>;
  let logout: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    user.set({ username: 'camille', status: 'PENDING_PASSWORD', permissions: [] });
    changePassword = vi.fn();
    logout = vi.fn(() => of(undefined));

    await TestBed.configureTestingModule({
      imports: [ChangePasswordPageComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { user, changePassword, logout } }],
    }).compileComponents();

    navigate = vi.fn().mockResolvedValue(true);
    TestBed.inject(Router).navigateByUrl = navigate;
  });

  function render() {
    const fixture = TestBed.createComponent(ChangePasswordPageComponent);
    fixture.detectChanges();
    return { fixture, compiled: fixture.nativeElement as HTMLElement };
  }

  function submit(fixture: ReturnType<typeof render>['fixture'], compiled: HTMLElement,
                  current: string, next: string, confirmation = next) {
    for (const [id, value] of [['current-password', current], ['new-password', next], ['confirmation', confirmation]]) {
      const input = compiled.querySelector(`#${ id }`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
    }
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('should explain a provisional password', () => {
    const { compiled } = render();

    expect(compiled.querySelector('h1')?.textContent).toContain('Choisissez votre mot de passe');
    expect(compiled.querySelector('label[for="current-password"]')?.textContent).toContain('Mot de passe provisoire');
    expect(compiled.querySelector('#new-password')?.getAttribute('autocomplete')).toBe('new-password');
  });

  it('should offer a way back to an active account', () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: [] });
    const { compiled } = render();

    expect(compiled.querySelector('h1')?.textContent).toContain('Changer de mot de passe');
    expect(compiled.querySelector('a.auth-secondary')?.getAttribute('href')).toBe('/dashboard');
  });

  it('should refuse a new password shorter than twelve characters', () => {
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'provisoire', 'trop court');

    expect(changePassword).not.toHaveBeenCalled();
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('de 12 à 128 caractères');
  });

  it('should refuse a confirmation that does not match', () => {
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'provisoire', 'un mot de passe solide', 'un mot de passe autre');

    expect(changePassword).not.toHaveBeenCalled();
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('ne correspond pas');
  });

  it('should change the password, confirm it and open the application', () => {
    changePassword.mockReturnValue(of({ username: 'camille', status: 'ACTIVE', permissions: [] }));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'provisoire', 'un mot de passe solide');

    expect(changePassword).toHaveBeenCalledWith({ currentPassword: 'provisoire', newPassword: 'un mot de passe solide' });
    expect(TestBed.inject(NotificationService).notifications()).toEqual([
      expect.objectContaining({ level: 'success', message: 'Mot de passe enregistré.' }),
    ]);
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('should show the reason given by the back on a 400', () => {
    changePassword.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Le mot de passe actuel est incorrect.' },
    })));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'pas le bon', 'un mot de passe solide');

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('Le mot de passe actuel est incorrect.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('should let a provisional account log out', () => {
    const { compiled } = render();

    compiled.querySelector<HTMLButtonElement>('button.auth-secondary')!.click();

    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/connexion');
  });
});
