import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { environment } from "../../../environments/environment";
import {
  CreatePublicationDeliveryRequest,
  CreatePublicationRequest,
  Publication,
  PublicationDelivery,
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

  // ---------------------------------------------------------------------------
  // Actions de la carte publication encore sans endpoint.
  //
  // Elles sont déclarées ici pour que la page soit complète et que le
  // branchement se résume, le jour venu, à remplacer le corps de la méthode par
  // l'appel HTTP décrit en commentaire. En attendant, elles échouent
  // explicitement plutôt que de faire croire à un succès.
  // ---------------------------------------------------------------------------

  /**
   * Génère le fichier XLSX récapitulatif d'une publication.
   *
   * TODO(back) : `POST /api/publications/{id}/xlsx` — renvoyer le fichier
   * (`responseType: 'blob'`) ou l'URL de téléchargement.
   */
  generateXlsx(publicationId: number): Observable<never> {
    return throwError(() => new Error(
      `Génération XLSX indisponible : aucun endpoint back pour la publication ${publicationId}.`,
    ));
  }

  /**
   * Rattache une publication à une campagne (ou l'en détache avec `null`).
   *
   * TODO(back) : `PATCH /api/publications/{id}` avec `{ campaignId }`.
   */
  assignToCampaign(publicationId: number, campaignId: number | null): Observable<never> {
    return throwError(() => new Error(
      `Assignation de campagne indisponible : aucun endpoint back (publication ${publicationId}, campagne ${campaignId}).`,
    ));
  }
}
