import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { filenameFromDisposition } from "../../core/files/save-file";
import { environment } from "../../../environments/environment";
import {
  CreatePublicationDeliveryRequest,
  CreatePublicationRequest,
  Publication,
  PublicationDelivery,
  SetPublicationCampaignRequest,
  SetPublicationMediasRequest,
  UpdatePublicationRequest,
} from "./publication.model";

@Injectable({
  providedIn: 'root',
})
export class PublicationsService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + "/publications"

  getPublications(): Observable<Publication[]> {
    return this.http.get<Publication[]>(this.url);
  }

  createPublication(request: CreatePublicationRequest): Observable<Publication> {
    return this.http.post<Publication>(this.url, request)
  }

  /** Remplace le texte et le statut ; les diffusions à venir partiront avec le nouveau texte. */
  updatePublication(publicationId: number, request: UpdatePublicationRequest): Observable<Publication> {
    return this.http.put<Publication>(`${ this.url }/${ publicationId }`, request);
  }

  /**
   * Supprime une publication et annule ses diffusions programmées. Le back
   * répond `409` si l'une d'elles est en cours ou imminente.
   */
  deletePublication(publicationId: number): Observable<void> {
    return this.http.delete<void>(`${ this.url }/${ publicationId }`);
  }

  /**
   * Diffuse immédiatement une publication sur la page Facebook.
   *
   * Le back crée une occurrence datée de l'instant présent pour tracer la
   * diffusion, puis renvoie la livraison correspondante (201). En cas de refus
   * du canal distant il répond 502 : la livraison est tout de même enregistrée
   * en base au statut `FAILED`, avec le message d'erreur de Facebook.
   */
  publishOnFacebook(publicationId: number): Observable<PublicationDelivery> {
    const body: CreatePublicationDeliveryRequest = { channel: 'FACEBOOK' };
    return this.http.post<PublicationDelivery>(`${ this.url }/${ publicationId }/deliveries`, body);
  }

  /**
   * Remplace la liste ordonnée des visuels de la publication.
   *
   * L'ordre du tableau fait foi : le premier média sert de vignette et de
   * première photo à la diffusion. Un tableau vide détache tout.
   */
  setMedias(publicationId: number, mediaIds: number[]): Observable<Publication> {
    const body: SetPublicationMediasRequest = { mediaIds };
    return this.http.put<Publication>(`${ this.url }/${ publicationId }/medias`, body);
  }

  /**
   * Exporte la publication au format d'import EventProxy (`.xlsx`). Le nom du
   * fichier vient du back (`Content-Disposition`) ; à défaut, un nom générique.
   */
  exportXlsx(publicationId: number): Observable<{ content: Blob, filename: string }> {
    return this.http.get(`${ this.url }/${ publicationId }/xlsx`, { responseType: 'blob', observe: 'response' }).pipe(
      map((response) => ({
        content: response.body ?? new Blob(),
        filename: filenameFromDisposition(response.headers.get('Content-Disposition'))
          ?? `EventProxy-publication-${ publicationId }.xlsx`,
      })),
    );
  }

  /**
   * Rattache une publication à une campagne, ou l'en détache avec `null`.
   *
   * L'appel remplace le rattachement, il ne l'ajoute pas : une publication
   * n'appartient qu'à une seule campagne.
   */
  assignToCampaign(publicationId: number, campaignId: number | null): Observable<Publication> {
    const body: SetPublicationCampaignRequest = { campaignId };
    return this.http.put<Publication>(`${ this.url }/${ publicationId }/campaign`, body);
  }
}
