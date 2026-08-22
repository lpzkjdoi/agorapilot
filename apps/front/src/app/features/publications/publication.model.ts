export type PublicationStatus = 'DRAFT' | 'VERIFIED';

/** Campagne telle qu'affichée sur une publication (badge de la carte). */
export interface PublicationCampaign {
  id: number
  name: string
}

export interface Publication {
  id: number,
  content: string,
  status: PublicationStatus,

  // Champs présents sur l'entité back mais **pas encore exposés** par
  // `PublicationDTO` (`id`, `content`, `status` uniquement). La maquette les
  // affiche : ils sont donc optionnels côté front, et les cartes se replient
  // proprement tant que l'API ne les renvoie pas.
  createdAt?: string,
  campaign?: PublicationCampaign | null,
}

export type CreatePublicationRequest = Omit<Publication, 'id' | 'createdAt' | 'campaign'>

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
