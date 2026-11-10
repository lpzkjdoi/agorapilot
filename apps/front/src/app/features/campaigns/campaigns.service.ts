import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from "../../../environments/environment";
import { Campaign, CampaignRequest } from "./campaign.model";

@Injectable({
  providedIn: 'root',
})
export class CampaignsService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/campaigns"

  getCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.url);
  }

  createCampaign(request: CampaignRequest): Observable<Campaign> {
    return this.http.post<Campaign>(this.url, request);
  }

  /** Remplace nom, description et période. */
  updateCampaign(id: number, request: CampaignRequest): Observable<Campaign> {
    return this.http.put<Campaign>(`${ this.url }/${ id }`, request);
  }

  /** Arrête une campagne en cours : sa date de fin passe au présent. */
  closeCampaign(id: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${ this.url }/${ id }/closure`, null);
  }

  cancelCampaign(id: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${ this.url }/${ id }/cancellation`, null);
  }

  /** « Supprime » la campagne : le back l'archive et en détache les publications. */
  archiveCampaign(id: number): Observable<void> {
    return this.http.delete<void>(`${ this.url }/${ id }`);
  }
}
