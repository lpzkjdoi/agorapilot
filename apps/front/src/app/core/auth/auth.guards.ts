import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUser, Permission } from "./auth.model";
import { map } from "rxjs";
import { AuthService } from "./auth.service";

export const LOGIN_PATH = '/connexion';
export const PASSWORD_PATH = '/mot-de-passe';
export const NO_ACCESS_PATH = '/aucun-droit';

/** Les pages d'arrivée possibles, dans l'ordre de préférence, avec le droit qu'elles exigent. */
const HOMES: [Permission, string][] = [
  ['publications:read', '/dashboard'],
  ['campaigns:read', '/campagnes'],
  ['medias:read', '/medias'],
  ['users:manage', '/comptes'],
];

/** La première page que le compte a le droit de voir ; sans aucun droit, une page qui le dit. */
export function homePath(user: CurrentUser | null): string {
  return HOMES.find(([permission]) => user?.permissions.includes(permission))?.[1] ?? NO_ACCESS_PATH;
}

/**
 * Pages de l'application : il faut être connecté, avec un mot de passe définitif.
 * Sinon direction la connexion (en retenant la page demandée), ou le changement
 * du mot de passe provisoire.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.currentUser().pipe(map((user) => {
    if (!user) {
      return router.createUrlTree([LOGIN_PATH], { queryParams: { redirect: state.url } });
    }

    return user.status === 'PENDING_PASSWORD' ? router.createUrlTree([PASSWORD_PATH]) : true;
  }));
};

/** Changement de mot de passe : réservé à qui est connecté. */
export const signedInGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.currentUser().pipe(map((user) => user ? true : router.createUrlTree([LOGIN_PATH])));
};

/** Page de connexion : inutile une fois connecté. */
export const signedOutGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.currentUser().pipe(map((user) => user ? router.createUrlTree([homePath(user)]) : true));
};

/**
 * Page réservée à un droit. Le back refuserait de toute façon ses appels : la
 * garde évite seulement d'afficher une page vide d'erreurs, et renvoie vers la
 * page d'arrivée du compte.
 */
export function permissionGuard(permission: Permission): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    return auth.currentUser().pipe(map((user) =>
      user?.permissions.includes(permission) ? true : router.createUrlTree([homePath(user)])));
  };
}
