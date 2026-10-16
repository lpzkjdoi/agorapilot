import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DeliveryChannel } from '../../features/publications/publication.model';
import { ChannelSuspension } from './channel-suspension.model';

/**
 * Canaux suspendus, partagés par toute l'application.
 *
 * L'état vit ici plutôt que dans le bandeau : une page qui vient d'essuyer un
 * refus (« Publier maintenant ») peut demander un rafraîchissement, et le
 * bandeau l'affiche aussitôt sans attendre son prochain passage.
 */
@Injectable({
  providedIn: 'root',
})
export class ChannelSuspensionsService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + '/channels';

  private readonly state = signal<readonly ChannelSuspension[]>([]);

  /** Les canaux suspendus ; vide quand tout fonctionne. */
  readonly suspensions = this.state.asReadonly();

  refresh(): Observable<ChannelSuspension[]> {
    return this.http
      .get<ChannelSuspension[]>(`${ this.url }/suspensions`)
      .pipe(tap((suspensions) => this.state.set(suspensions)));
  }

  /** Lève la suspension : les diffusions en attente repartent au prochain balayage. */
  lift(channel: DeliveryChannel): Observable<void> {
    return this.http
      .delete<void>(`${ this.url }/${ channel }/suspension`)
      .pipe(tap(() => this.state.update((list) => list.filter((s) => s.channel !== channel))));
  }
}
