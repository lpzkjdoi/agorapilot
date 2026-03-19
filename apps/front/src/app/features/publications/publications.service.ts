import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CreatePublicationRequest, Publication } from "./publication.model";

@Injectable({
  providedIn: 'root',
})
export class PublicationsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/publications';

  private readonly url = this.apiUrl + "/publications"

  getPublications(): Observable<Publication[]> {
    return this.http.get<Publication[]>(this.url);
  }

  createPublication(request: CreatePublicationRequest): Observable<Publication> {
    return this.http.post<Publication>(this.url, request)
  }
}