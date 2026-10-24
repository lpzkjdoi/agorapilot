import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, Observable, of } from 'rxjs';
import { authGuard, signedInGuard, signedOutGuard } from './auth.guards';
import { CurrentUser } from './auth.model';
import { AuthService } from './auth.service';

type GuardResult = boolean | UrlTree;

describe('auth guards', () => {
  let current: CurrentUser | null;

  beforeEach(() => {
    current = null;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: () => of(current) } },
      ],
    });
  });

  async function run(guard: typeof authGuard, url = '/publications'): Promise<string | true> {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot));
    const value = isObservable(result) ? await firstValueFrom(result as Observable<GuardResult>) : result as GuardResult;

    return value instanceof UrlTree ? value.toString() : value as true;
  }

  describe('authGuard', () => {
    it('should send a signed-out visitor to the login page with the requested url', async () => {
      expect(await run(authGuard)).toBe('/connexion?redirect=%2Fpublications');
    });

    it('should send a provisional password to the password page', async () => {
      current = { username: 'camille', status: 'PENDING_PASSWORD', permissions: [] };
      expect(await run(authGuard)).toBe('/mot-de-passe');
    });

    it('should let an active account in', async () => {
      current = { username: 'camille', status: 'ACTIVE', permissions: [] };
      expect(await run(authGuard)).toBe(true);
    });
  });

  describe('signedInGuard', () => {
    it('should require a signed-in account', async () => {
      expect(await run(signedInGuard)).toBe('/connexion');

      current = { username: 'camille', status: 'PENDING_PASSWORD', permissions: [] };
      expect(await run(signedInGuard)).toBe(true);
    });
  });

  describe('signedOutGuard', () => {
    it('should send a signed-in account to the dashboard', async () => {
      expect(await run(signedOutGuard)).toBe(true);

      current = { username: 'camille', status: 'ACTIVE', permissions: [] };
      expect(await run(signedOutGuard)).toBe('/dashboard');
    });
  });
});
