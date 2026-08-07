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

/** Valeurs du filtre « statut » de la page Publications. */
export type PublicationStatusFilter = 'ALL' | PublicationStatus;

/** Valeur du filtre « campagne » : `ALL`, `NONE` (sans campagne) ou un id. */
export type PublicationCampaignFilter = 'ALL' | 'NONE' | number;
