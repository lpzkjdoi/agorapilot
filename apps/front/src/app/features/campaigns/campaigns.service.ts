import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from "../../../environments/environment";
import { Campaign } from "./campaign.model";

@Injectable({
  providedIn: 'root',
})
export class CampaignsService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/campaigns"

  getCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.url);
  }
}
