import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from '@angular/core';
import { environment } from "../../../environments/environment";
import { WeeklyOccurrences } from "./occurrence.model";

@Injectable({
  providedIn: 'root',
})
export class OccurrencesService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/occurrences"

  getWeeklyOccurrences() {
    return this.http.get<WeeklyOccurrences>(this.url + "/weekly")
  }
}
