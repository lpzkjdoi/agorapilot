import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from '@angular/core';
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import {
  CreateOccurrenceRequest,
  CreateOccurrencesRequest,
  CreateSeriesRequest,
  Occurrence,
  OccurrenceSeries,
  RescheduleOccurrenceRequest,
  WeeklyOccurrences,
} from "./occurrence.model";

@Injectable({
  providedIn: 'root',
})
export class OccurrencesService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/occurrences"
  private readonly seriesUrl = environment.apiUrl + "/occurrence-series"

  getWeeklyOccurrences() {
    return this.http.get<WeeklyOccurrences>(this.url + "/weekly")
  }

  /** Occurrences de `[from, to)` (dates ISO), dans l'ordre chronologique. */
  getBetween(from: string, to: string): Observable<Occurrence[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Occurrence[]>(this.url, { params });
  }

  /** Programme une diffusion ; le back répond avec l'heure retenue. */
  create(request: CreateOccurrenceRequest): Observable<Occurrence> {
    return this.http.post<Occurrence>(this.url, request);
  }

  /**
   * Programme une publication à plusieurs dates d'un coup. Tout ou rien : une
   * date refusée n'en programme aucune, et le motif la nomme.
   */
  createAll(request: CreateOccurrencesRequest): Observable<Occurrence[]> {
    return this.http.post<Occurrence[]>(this.url + "/batch", request);
  }

  /**
   * Change le jour d'une diffusion, fixe son heure, ou la rend automatique
   * (`time: null`). Les autres diffusions des jours concernés sont réparties à
   * nouveau par le back : recharger le calendrier ensuite.
   */
  reschedule(occurrenceId: number, request: RescheduleOccurrenceRequest): Observable<Occurrence> {
    return this.http.put<Occurrence>(`${ this.url }/${ occurrenceId }/schedule`, request);
  }

  /**
   * Reprend une diffusion en échec au jour demandé, heure automatique
   * (`time: null`) ou fixée : ses canaux en échec repartent en attente.
   */
  retry(occurrenceId: number, request: RescheduleOccurrenceRequest): Observable<Occurrence> {
    return this.http.post<Occurrence>(`${ this.url }/${ occurrenceId }/retry`, request);
  }

  /**
   * Programme une publication par fréquence : le back crée la série et ses
   * premières diffusions, puis les suivantes chaque nuit.
   */
  createSeries(request: CreateSeriesRequest): Observable<OccurrenceSeries> {
    return this.http.post<OccurrenceSeries>(this.seriesUrl, request);
  }

  /** Les séries d'une publication, la plus récente d'abord. */
  getSeries(publicationId: number): Observable<OccurrenceSeries[]> {
    return this.http.get<OccurrenceSeries[]>(this.seriesUrl, { params: new HttpParams().set('publicationId', publicationId) });
  }

  /** Arrête une série : ses diffusions encore programmées sont annulées. */
  stopSeries(seriesId: number): Observable<OccurrenceSeries> {
    return this.http.delete<OccurrenceSeries>(`${ this.seriesUrl }/${ seriesId }`);
  }

  /** Annule une diffusion programmée ; le jour est réparti à nouveau. */
  cancel(occurrenceId: number): Observable<void> {
    return this.http.delete<void>(`${ this.url }/${ occurrenceId }`);
  }
}
