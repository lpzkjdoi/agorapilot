import { DeliveryStatus, Publication } from "../publications/publication.model";

interface Delivery {
  id: number,
  channel: "FACEBOOK" | "INTRAMUROS"
  status: DeliveryStatus
}

export interface Occurrence {
  id: number,
  scheduledAt: string,
  /** Heure fixée à la main ; sinon placée automatiquement dans la fenêtre du jour. */
  pinned: boolean,
  status: "SCHEDULED" | "PUBLISHED" | "FAILED"
  publication: Publication
  deliveries: Delivery[]
}

export type WeeklyOccurrences = Map<string, Occurrence[]>