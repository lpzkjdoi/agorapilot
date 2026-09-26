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

/** Corps de `POST /api/campaigns`. Les dates sont des `LocalDateTime` ISO. */
export interface CreateCampaignRequest {
  name: string
  description: string
  startDate: string | null
  endDate: string | null
}

export interface CreateCampaignFormValue {
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
