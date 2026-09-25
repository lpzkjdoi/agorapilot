import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from '@angular/core';
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import {
  CreateOccurrenceRequest,
  Occurrence,
  RescheduleOccurrenceRequest,
  WeeklyOccurrences,
} from "./occurrence.model";

@Injectable({
  providedIn: 'root',
})
export class OccurrencesService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/occurrences"

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
   * Change le jour d'une diffusion, fixe son heure, ou la rend automatique
   * (`time: null`). Les autres diffusions des jours concernés sont réparties à
   * nouveau par le back : recharger le calendrier ensuite.
   */
  reschedule(occurrenceId: number, request: RescheduleOccurrenceRequest): Observable<Occurrence> {
    return this.http.put<Occurrence>(`${ this.url }/${ occurrenceId }/schedule`, request);
  }

  /** Annule une diffusion programmée ; le jour est réparti à nouveau. */
  cancel(occurrenceId: number): Observable<void> {
    return this.http.delete<void>(`${ this.url }/${ occurrenceId }`);
  }
}
