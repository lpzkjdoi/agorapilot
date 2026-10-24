import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from '@angular/core';
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import { Permission } from "../../core/auth/auth.model";
import { Account, CreateAccountRequest, SecurityEvent, TemporaryPassword } from "./user.model";

/** Administration des comptes (`users:manage`). */
@Injectable({
  providedIn: 'root',
})
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/users";

  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(this.url);
  }

  /** Le back répond avec le mot de passe provisoire, à transmettre à la personne. */
  create(request: CreateAccountRequest): Observable<TemporaryPassword> {
    return this.http.post<TemporaryPassword>(this.url, request);
  }

  /** Remplacement complet : les droits absents de la liste sont retirés. */
  setPermissions(accountId: number, permissions: Permission[]): Observable<Account> {
    return this.http.put<Account>(`${ this.url }/${ accountId }/permissions`, { permissions });
  }

  disable(accountId: number): Observable<Account> {
    return this.http.post<Account>(`${ this.url }/${ accountId }/disable`, null);
  }

  /** Nouveau mot de passe provisoire ; réactive aussi un compte désactivé. */
  resetPassword(accountId: number): Observable<TemporaryPassword> {
    return this.http.post<TemporaryPassword>(`${ this.url }/${ accountId }/password-reset`, null);
  }

  /** Le journal de sécurité, le plus récent d'abord. */
  getEvents(limit = 50): Observable<SecurityEvent[]> {
    return this.http.get<SecurityEvent[]>(environment.apiUrl + "/security-events", {
      params: new HttpParams().set('limit', limit),
    });
  }
}
