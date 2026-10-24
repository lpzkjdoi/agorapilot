export type PublicationStatus = 'DRAFT' | 'VERIFIED';

/** Campagne telle qu'affichée sur une publication (badge de la carte). */
export interface PublicationCampaign {
  id: number
  name: string
}

/**
 * Visuel rattaché à une publication, tel que le back l'embarque
 * (`MediaSummaryDTO`) : la forme réduite du média, sans empreinte ni dates.
 */
export interface PublicationMedia {
  id: number
  title: string | null
  originalFilename: string
  contentType: string
  width: number | null
  height: number | null
  altText: string | null
}

export interface Publication {
  id: number,
  content: string,
  status: PublicationStatus,

  /** Visuels rattachés, dans l'ordre choisi ; vide s'il n'y en a aucun. */
  medias: PublicationMedia[],

  /** Campagne de rattachement, `null` si la publication n'en a pas. */
  campaign: PublicationCampaign | null,

  // Traçabilité. Optionnels : une publication antérieure n'a pas d'auteur, et les
  // cartes se replient proprement sans eux.
  createdAt?: string | null,
  /** Pseudo de l'auteur. */
  createdBy?: string | null,
  updatedAt?: string | null,
  /** Pseudo du dernier à l'avoir modifiée. */
  updatedBy?: string | null,
}

export type CreatePublicationRequest = Pick<Publication, 'content' | 'status'>

/** Corps de `PUT /api/publications/{id}` : remplacement complet du texte et du statut. */
export type UpdatePublicationRequest = Pick<Publication, 'content' | 'status'>

/** Corps de `PUT /api/publications/{id}/campaign` : `null` détache la publication. */
export interface SetPublicationCampaignRequest {
  campaignId: number | null
}

export interface CreatePublicationFormValue {
  content: Publication['content']
  status: boolean
}

/** Canaux de diffusion connus du back (`DeliveryChannel`). */
export type DeliveryChannel = 'FACEBOOK' | 'INTRAMUROS';

/**
 * Statut d'une livraison (`DeliveryStatus`). `IN_PROGRESS` : prise en charge
 * par l'ordonnanceur, appel au canal en cours.
 */
export type DeliveryStatus = 'PENDING' | 'IN_PROGRESS' | 'PUBLISHED' | 'FAILED';

/**
 * Livraison renvoyée par `POST /api/publications/{id}/deliveries`.
 *
 * Le back trace toute diffusion — y compris une diffusion immédiate — sous la
 * forme d'une occurrence datée de l'instant présent portant cette livraison.
 */
export interface PublicationDelivery {
  id: number
  occurrenceId: number
  channel: DeliveryChannel
  status: DeliveryStatus
  publishedAt: string | null
  externalId: string | null
  /** Motif de l'échec quand `status` vaut `FAILED`. */
  errorMessage: string | null
}

/** Corps de `POST /api/publications/{id}/deliveries`. */
export interface CreatePublicationDeliveryRequest {
  channel: DeliveryChannel
}

/** Valeurs du filtre « statut » de la page Publications. */
export type PublicationStatusFilter = 'ALL' | PublicationStatus;

/** Valeur du filtre « campagne » : `ALL`, `NONE` (sans campagne) ou un id. */
export type PublicationCampaignFilter = 'ALL' | 'NONE' | number;

/** Corps de `PUT /api/publications/{id}/medias` : l'ordre de la liste fait foi. */
export interface SetPublicationMediasRequest {
  mediaIds: number[]
}

/** Un visuel non diffusable tel quel : le PDF n'est pas publiable comme photo. */
export function isPublishableMedia(media: PublicationMedia): boolean {
  return media.contentType.startsWith('image/');
}
