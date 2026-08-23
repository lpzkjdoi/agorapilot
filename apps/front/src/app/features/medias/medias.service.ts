import { HttpClient, HttpEvent } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Media, MediaUploadMetadata, UpdateMediaRequest } from './media.model';

@Injectable({
  providedIn: 'root',
})
export class MediasService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + '/medias';

  /**
   * @param archived `false` pour la médiathèque courante, `true` pour les seuls
   *                 médias archivés, omis pour tout obtenir.
   */
  getMedias(archived?: boolean): Observable<Media[]> {
    const options = archived === undefined ? {} : { params: { archived } };
    return this.http.get<Media[]>(this.url, options);
  }

  /**
   * URL du binaire. Elle se dérive de l'identifiant plutôt que d'être stockée en
   * base : une URL absolue persistée deviendrait fausse au premier changement de
   * nom de domaine.
   */
  fileUrl(mediaId: number): string {
    return `${ this.url }/${ mediaId }/file`;
  }

  /**
   * Dépose un fichier.
   * <p>
   * Renvoie le flux d'événements HTTP et non la seule réponse : une affiche pèse
   * plusieurs mégaoctets, l'avancement est la seule chose à montrer pendant ce
   * temps.
   */
  upload(file: File, metadata: MediaUploadMetadata = {}): Observable<HttpEvent<Media>> {
    const body = new FormData();
    body.append('file', file, file.name);

    if (metadata.title) {
      body.append('title', metadata.title);
    }

    if (metadata.altText) {
      body.append('altText', metadata.altText);
    }

    return this.http.post<Media>(this.url, body, {
      reportProgress: true,
      observe: 'events',
    });
  }

  updateMedia(mediaId: number, request: UpdateMediaRequest): Observable<Media> {
    return this.http.patch<Media>(`${ this.url }/${ mediaId }`, request);
  }

  deleteMedia(mediaId: number): Observable<void> {
    return this.http.delete<void>(`${ this.url }/${ mediaId }`);
  }
}
