import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable, InjectionToken } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FacebookConnectionStatus } from './facebook-channel.model';

/**
 * Envoie le navigateur hors de l'application. Isolé derrière un jeton pour que
 * les specs le remplacent : jsdom ne sait pas naviguer.
 */
export const BROWSER_REDIRECT = new InjectionToken<(url: string) => void>('BROWSER_REDIRECT', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return (url: string) => document.location.assign(url);
  },
});

/** Connexion de la Page Facebook depuis l'interface (`channels:manage`). */
@Injectable({
  providedIn: 'root',
})
export class FacebookChannelService {
  private readonly http = inject(HttpClient);
  private readonly redirect = inject(BROWSER_REDIRECT);
  private readonly url = environment.apiUrl + '/channels/facebook/connection';

  getStatus(): Observable<FacebookConnectionStatus> {
    return this.http.get<FacebookConnectionStatus>(this.url);
  }

  /** Contrôle le jeton auprès de Facebook sans attendre le passage quotidien. */
  check(): Observable<FacebookConnectionStatus> {
    return this.http.post<FacebookConnectionStatus>(`${ this.url }/check`, null);
  }

  /**
   * Ouvre une demande de connexion, puis quitte l'application pour la boîte de
   * dialogue Facebook. Facebook ramène ensuite le navigateur sur
   * `/canal-facebook?connexion=ok|erreur`.
   */
  connect(): Observable<void> {
    return this.http
      .post<{ authorizationUrl: string }>(this.url, null)
      .pipe(map(({ authorizationUrl }) => this.redirect(authorizationUrl)));
  }
}
