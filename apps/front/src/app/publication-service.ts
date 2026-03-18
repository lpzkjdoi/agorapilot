import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Publication } from "./types/publication.type";

@Injectable({
  providedIn: 'root',
})
export class PublicationService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/publications';

  getPublications(): Observable<Publication[]> {
    return this.http.get<Publication[]>(this.apiUrl);
  }
}