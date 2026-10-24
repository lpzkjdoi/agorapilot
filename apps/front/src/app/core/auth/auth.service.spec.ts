import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CurrentUser } from './auth.model';
import { AuthService } from './auth.service';

const camille: CurrentUser = { username: 'camille', status: 'ACTIVE', permissions: ['publications:read'] };

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should read the current user once, however many callers ask', () => {
    const received: (CurrentUser | null)[] = [];
    service.currentUser().subscribe((user) => received.push(user));
    service.currentUser().subscribe((user) => received.push(user));

    httpTesting.expectOne('/api/me').flush(camille);

    expect(received).toEqual([camille, camille]);
    expect(service.user()).toEqual(camille);
    expect(service.active()).toBe(true);
  });

  it('should take a 401 on /api/me as « nobody is signed in », not as an error', () => {
    let received: CurrentUser | null | undefined;
    service.currentUser().subscribe((user) => (received = user));

    httpTesting.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(received).toBeNull();
    expect(service.user()).toBeNull();
  });

  it('should let any other error through', () => {
    let failed = false;
    service.currentUser().subscribe({ error: () => (failed = true) });

    httpTesting.expectOne('/api/me').flush(null, { status: 500, statusText: 'Server Error' });

    expect(failed).toBe(true);
  });

  it('should POST the credentials and remember the account', () => {
    service.login({ username: 'camille', password: 'secret' }).subscribe();

    const req = httpTesting.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'camille', password: 'secret' });
    req.flush(camille);

    expect(service.user()).toEqual(camille);
    // Plus besoin de relire `/api/me` pour les gardes.
    service.currentUser().subscribe();
  });

  it('should not count a provisional password as active', () => {
    service.login({ username: 'camille', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/auth/login').flush({ ...camille, status: 'PENDING_PASSWORD', permissions: [] });

    expect(service.active()).toBe(false);
  });

  it('should forget the account on logout, even if the call fails', () => {
    service.login({ username: 'camille', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/auth/login').flush(camille);

    let done = false;
    service.logout().subscribe(() => (done = true));
    const req = httpTesting.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 500, statusText: 'Server Error' });

    expect(done).toBe(true);
    expect(service.user()).toBeNull();
  });

  it('should PUT the passwords, then read the account again', () => {
    let received: CurrentUser | undefined;
    service.changePassword({ currentPassword: 'old', newPassword: 'a brand new one' })
           .subscribe((user) => (received = user));

    const put = httpTesting.expectOne('/api/me/password');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ currentPassword: 'old', newPassword: 'a brand new one' });
    put.flush(null, { status: 204, statusText: 'No Content' });

    httpTesting.expectOne('/api/me').flush(camille);
    expect(received).toEqual(camille);
    expect(service.active()).toBe(true);
  });

  it('should tell whether the account holds a permission', () => {
    expect(service.can('publications:read')).toBe(false);

    service.login({ username: 'camille', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/auth/login').flush(camille);

    expect(service.can('publications:read')).toBe(true);
    expect(service.can('users:manage')).toBe(false);
  });
});
