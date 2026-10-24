import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from "rxjs";
import { NotificationService } from "../notifications/notification.service";
import { LOGIN_PATH } from "./auth.guards";
import { AuthService } from "./auth.service";

/** Ces requêtes répondent 401 dans leur fonctionnement normal : elles décident elles-mêmes. */
const AUTH_URLS = ['/api/me', '/api/auth/login'];

/**
 * `401` : la session a expiré ou a été coupée (compte désactivé, mot de passe
 * changé ailleurs) — retour à la connexion, en retenant la page courante.
 *
 * `403` sur une action : l'utilisateur n'a pas le droit. Une lecture refusée
 * n'est pas notifiée ici : la page qui l'a demandée affiche déjà son erreur, et
 * le bandeau des suspensions interroge le back chaque minute.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  return next(request).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && !AUTH_URLS.some((url) => request.url.endsWith(url))) {
        if (err.status === 401 && auth.user()) {
          auth.forget();
          void router.navigate([LOGIN_PATH], { queryParams: { redirect: router.url } });
        } else if (err.status === 403 && request.method !== 'GET') {
          notifications.error('Vous n’avez pas le droit d’effectuer cette action.');
        }
      }

      return throwError(() => err);
    }),
  );
};
