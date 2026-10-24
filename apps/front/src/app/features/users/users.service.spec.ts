import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Account } from './user.model';
import { UsersService } from './users.service';

const camille: Account = {
  id: 2, username: 'camille', status: 'ACTIVE', permissions: ['publications:read'],
  lockedUntil: null, lastLoginAt: null, createdAt: '2026-10-24T09:00:00',
};

describe('UsersService', () => {
  let service: UsersService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UsersService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should GET the accounts', () => {
    let received: Account[] | undefined;
    service.getAccounts().subscribe((accounts) => (received = accounts));

    httpTesting.expectOne('/api/users').flush([camille]);
    expect(received).toEqual([camille]);
  });

  it('should POST a new account with its permissions', () => {
    service.create({ username: 'camille', permissions: ['publications:read'] }).subscribe();

    const req = httpTesting.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'camille', permissions: ['publications:read'] });
    req.flush({ account: camille, temporaryPassword: 'abcd-efgh-jkmn-pqrs' });
  });

  it('should PUT the full list of permissions', () => {
    service.setPermissions(2, ['medias:read']).subscribe();

    const req = httpTesting.expectOne('/api/users/2/permissions');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ permissions: ['medias:read'] });
    req.flush(camille);
  });

  it('should POST the disabling and the password reset', () => {
    service.disable(2).subscribe();
    service.resetPassword(2).subscribe();

    expect(httpTesting.expectOne('/api/users/2/disable').request.method).toBe('POST');
    expect(httpTesting.expectOne('/api/users/2/password-reset').request.method).toBe('POST');
  });

  it('should GET the latest security events', () => {
    service.getEvents(20).subscribe();

    const req = httpTesting.expectOne((request) => request.url === '/api/security-events');
    expect(req.request.params.get('limit')).toBe('20');
    req.flush([]);
  });
});
