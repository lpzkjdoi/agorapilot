import { computed, Provider, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ALL_PERMISSIONS } from '../../features/users/permissions';
import { CurrentUser, Permission } from './auth.model';
import { AuthService } from './auth.service';

/**
 * Pour les specs : un compte actif connecté, avec exactement ces droits (tous par
 * défaut). `user` reste modifiable pour changer de compte en cours de test.
 */
export function fakeAuth(permissions: Permission[] = ALL_PERMISSIONS, username = 'camille') {
  const user = signal<CurrentUser | null>({ username, status: 'ACTIVE', permissions });

  return {
    user,
    active: computed(() => user()?.status === 'ACTIVE'),
    can: (permission: Permission) => user()?.permissions.includes(permission) ?? false,
    currentUser: () => of(user()),
    logout: () => of(undefined),
    forget: () => user.set(null),
  };
}

export function provideSignedInAs(permissions: Permission[] = ALL_PERMISSIONS): Provider {
  return { provide: AuthService, useValue: fakeAuth(permissions) };
}

/** Change, en cours de test, les droits du compte fourni par {@link provideSignedInAs}. */
export function signInAs(permissions: Permission[]): void {
  const auth = TestBed.inject(AuthService) as unknown as ReturnType<typeof fakeAuth>;
  auth.user.update((user) => user && { ...user, permissions });
}
