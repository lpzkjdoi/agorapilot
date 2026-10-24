import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of, shareReplay, switchMap, tap, throwError } from "rxjs";
import { environment } from "../../../environments/environment";
import { ChangePasswordRequest, CurrentUser, LoginRequest, Permission } from "./auth.model";

/**
 * Le compte connecté, partagé par toute l'application.
 *
 * Il n'est qu'un reflet de la session côté serveur, qui seule fait foi : le back
 * relit droits et statut à chaque requête. Le front s'en sert pour savoir où
 * envoyer l'utilisateur et quoi lui montrer, jamais pour autoriser.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl;

  private readonly state = signal<CurrentUser | null>(null);
  /** Lecture de `/api/me` en cours ou faite : une seule requête pour toutes les gardes. */
  private session$: Observable<CurrentUser | null> | null = null;

  /** `null` quand personne n'est connecté (ou tant que ce n'est pas vérifié). */
  readonly user = this.state.asReadonly();

  /** Connecté, mot de passe définitif : l'application complète est accessible. */
  readonly active = computed(() => this.state()?.status === 'ACTIVE');

  /**
   * Le compte de la session courante, lu une seule fois. Un `401` n'est pas une
   * erreur : il signifie simplement que personne n'est connecté.
   */
  currentUser(): Observable<CurrentUser | null> {
    this.session$ ??= this.http.get<CurrentUser>(`${ this.url }/me`).pipe(
      catchError((err: HttpErrorResponse) => err.status === 401 ? of(null) : throwError(() => err)),
      tap((user) => this.state.set(user)),
      shareReplay(1),
    );

    return this.session$;
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.http.post<CurrentUser>(`${ this.url }/auth/login`, request).pipe(
      tap((user) => this.remember(user)),
    );
  }

  /** La session est fermée côté serveur ; ici on oublie le compte quoi qu'il arrive. */
  logout(): Observable<void> {
    return this.http.post<void>(`${ this.url }/auth/logout`, null).pipe(
      catchError(() => of(undefined)),
      map(() => this.forget()),
    );
  }

  /** Un mot de passe provisoire devient définitif : le compte relu exerce enfin ses droits. */
  changePassword(request: ChangePasswordRequest): Observable<CurrentUser> {
    return this.http.put<void>(`${ this.url }/me/password`, request).pipe(
      tap(() => (this.session$ = null)),
      // Relu plutôt que deviné : le statut et les droits viennent du back.
      switchMap(() => this.currentUser()),
      map((user) => {
        if (!user) {
          throw new Error('Session perdue après le changement de mot de passe.');
        }
        return user;
      }),
    );
  }

  can(permission: Permission): boolean {
    return this.state()?.permissions.includes(permission) ?? false;
  }

  /** La session a expiré ou a été coupée côté serveur. */
  forget(): void {
    this.state.set(null);
    this.session$ = of(null);
  }

  private remember(user: CurrentUser): void {
    this.state.set(user);
    this.session$ = of(user);
  }
}

