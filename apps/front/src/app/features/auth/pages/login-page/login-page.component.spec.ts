import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { CurrentUser } from '../../../../core/auth/auth.model';
import { AuthService } from '../../../../core/auth/auth.service';
import { LoginPageComponent } from './login-page.component';

describe('LoginPageComponent', () => {
  let login: ReturnType<typeof vi.fn<(request: unknown) => Observable<CurrentUser>>>;
  let navigate: ReturnType<typeof vi.fn>;
  let redirect: string | null;

  beforeEach(async () => {
    login = vi.fn();
    redirect = null;

    await TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { get queryParamMap() { return convertToParamMap(redirect ? { redirect } : {}); } } },
        },
      ],
    }).compileComponents();

    navigate = vi.fn().mockResolvedValue(true);
    TestBed.inject(Router).navigateByUrl = navigate;
  });

  function render() {
    const fixture = TestBed.createComponent(LoginPageComponent);
    fixture.detectChanges();
    return { fixture, compiled: fixture.nativeElement as HTMLElement };
  }

  function submit(fixture: ReturnType<typeof render>['fixture'], compiled: HTMLElement, username: string, password: string) {
    for (const [id, value] of [['username', username], ['password', password]]) {
      const input = compiled.querySelector(`#${ id }`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('should render the login form with the right autocomplete hints', () => {
    const { compiled } = render();

    expect(compiled.querySelector('h1')?.textContent).toContain('Connexion');
    expect(compiled.querySelector('#username')?.getAttribute('autocomplete')).toBe('username');
    expect(compiled.querySelector('#password')?.getAttribute('type')).toBe('password');
    expect(compiled.querySelector('#password')?.getAttribute('autocomplete')).toBe('current-password');
  });

  it('should not call the back with an empty form', () => {
    const { fixture, compiled } = render();

    submit(fixture, compiled, '', '');

    expect(login).not.toHaveBeenCalled();
    expect(compiled.querySelector('.auth-error')?.textContent).toContain('Saisissez votre pseudo');
  });

  it('should sign in and go to the dashboard', () => {
    login.mockReturnValue(of({ username: 'camille', status: 'ACTIVE', permissions: [] }));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'camille', 'correct horse battery');

    expect(login).toHaveBeenCalledWith({ username: 'camille', password: 'correct horse battery' });
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('should go back to the page that was requested', () => {
    redirect = '/calendrier?mois=2026-11';
    login.mockReturnValue(of({ username: 'camille', status: 'ACTIVE', permissions: [] }));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'camille', 'correct horse battery');

    expect(navigate).toHaveBeenCalledWith('/calendrier?mois=2026-11');
  });

  it('should ignore a redirect that leaves the application', () => {
    redirect = '//evil.example/phishing';
    login.mockReturnValue(of({ username: 'camille', status: 'ACTIVE', permissions: [] }));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'camille', 'correct horse battery');

    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('should send a provisional password to the password page', () => {
    login.mockReturnValue(of({ username: 'camille', status: 'PENDING_PASSWORD', permissions: [] }));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'camille', 'mot de passe provisoire');

    expect(navigate).toHaveBeenCalledWith('/mot-de-passe');
  });

  it('should show the reason of a 401 and clear the password', () => {
    login.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 401, error: { message: 'Identifiant ou mot de passe incorrect.' },
    })));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'camille', 'mauvais');

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('Identifiant ou mot de passe incorrect.');
    expect((compiled.querySelector('#password') as HTMLInputElement).value).toBe('');
    expect(compiled.querySelectorAll('[role="alert"]').length).toBe(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('should show a generic message when the back is unreachable', () => {
    login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const { fixture, compiled } = render();

    submit(fixture, compiled, 'camille', 'correct horse battery');

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('Connexion impossible pour le moment');
  });
});
