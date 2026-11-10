import { Publication } from "../publications/publication.model";

export type CampaignStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'SCHEDULED' | 'CANCELED';

/** Reflète `CampaignDTO` côté back (`GET /api/campaigns`). */
export interface Campaign {
  id: number
  name: string
  description: string
  startDate: string | null
  endDate: string | null
  status: CampaignStatus
  publications: Publication[]
}

/**
 * Corps de `POST /api/campaigns` et de `PUT /api/campaigns/{id}`. Les dates
 * sont des `LocalDateTime` ISO.
 */
export interface CampaignRequest {
  name: string
  description: string
  startDate: string | null
  endDate: string | null
}

/** Valeur du formulaire de campagne : dates au format `AAAA-MM-JJ`, vides si absentes. */
export interface CampaignFormValue {
  name: string
  description: string
  startDate: string
  endDate: string
}

export const CAMPAIGN_STATUS_LABELS: Record<CampaignStatus, string> = {
  DRAFT: 'Brouillon',
  SCHEDULED: 'Programmée',
  ACTIVE: 'En cours',
  COMPLETED: 'Terminée',
  CANCELED: 'Annulée',
};

/** Actions du détail d'une campagne qui demandent une confirmation. */
export type CampaignAction = 'close' | 'cancel' | 'archive';

/** Une campagne en cours peut être clôturée : arrêtée maintenant. */
export function canClose(campaign: Campaign): boolean {
  return campaign.status === 'ACTIVE';
}

/** Seule une campagne terminée ou déjà annulée ne peut plus l'être. */
export function canCancel(campaign: Campaign): boolean {
  return campaign.status !== 'COMPLETED' && campaign.status !== 'CANCELED';
}

/** Compteurs affichés en tête du détail d'une campagne. */
export interface CampaignStats {
  total: number
  verified: number
  drafts: number
}

export function campaignStats(campaign: Campaign): CampaignStats {
  const publications = campaign.publications;

  return {
    total: publications.length,
    verified: publications.filter((publication) => publication.status === 'VERIFIED').length,
    drafts: publications.filter((publication) => publication.status === 'DRAFT').length,
  };
}
