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
