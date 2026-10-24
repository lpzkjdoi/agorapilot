import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NotificationService } from '../notifications/notification.service';
import { authInterceptor } from './auth.interceptor';
import { CurrentUser } from './auth.model';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let navigate: ReturnType<typeof vi.fn>;
  let notifications: NotificationService;
  const user = signal<CurrentUser | null>(null);
  const forget = vi.fn();

  beforeEach(() => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: [] });
    forget.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user, forget } },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.inject(Router).navigate = navigate;
  });

  afterEach(() => httpTesting.verify());

  function fail(method: 'GET' | 'POST', url: string, status: number): void {
    const request = method === 'GET' ? http.get(url) : http.post(url, {});
    request.subscribe({ error: () => undefined });
    httpTesting.expectOne(url).flush(null, { status, statusText: 'Error' });
  }

  it('should forget the account and go to the login page on a 401', () => {
    fail('GET', '/api/publications', 401);

    expect(forget).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { redirect: '/' } });
  });

  it('should leave the 401 of /api/me and of the login to their callers', () => {
    fail('GET', '/api/me', 401);
    fail('POST', '/api/auth/login', 401);

    expect(forget).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('should not redirect when nobody was signed in', () => {
    user.set(null);
    fail('GET', '/api/publications', 401);

    expect(navigate).not.toHaveBeenCalled();
  });

  it('should notify a forbidden action', () => {
    fail('POST', '/api/publications', 403);

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'error', message: 'Vous n’avez pas le droit d’effectuer cette action.' }),
    ]);
  });

  it('should not notify a forbidden read, already shown by its page', () => {
    fail('GET', '/api/publications', 403);

    expect(notifications.notifications()).toEqual([]);
  });

  it('should still hand the error to the caller', () => {
    let status = 0;
    http.post('/api/publications', {}).subscribe({ error: (err) => (status = err.status) });
    httpTesting.expectOne('/api/publications').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
  });
});
