import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from '@angular/core';
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";
import { DashboardSummary } from "./dashboard.model";

@Injectable({
  providedIn: 'root',
})
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/dashboard"

  /** Compteurs et prochaines diffusions du tableau de bord. */
  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(this.url);
  }
}
