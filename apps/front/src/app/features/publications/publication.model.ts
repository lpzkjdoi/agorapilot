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

  // Présent sur l'entité back mais **pas encore exposé** par `PublicationDTO`.
  // La maquette l'affiche : il reste optionnel côté front, et les cartes se
  // replient proprement tant que l'API ne le renvoie pas.
  createdAt?: string,
}

export type CreatePublicationRequest = Omit<Publication, 'id' | 'createdAt' | 'campaign' | 'medias'>

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

/** Statut d'une livraison (`DeliveryStatus`). */
export type DeliveryStatus = 'PENDING' | 'PUBLISHED' | 'FAILED';

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
