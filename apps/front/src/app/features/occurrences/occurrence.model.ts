import { Publication } from "../publications/publication.model";

interface Delivery {
  id: number,
  channel: "FACEBOOK" | "INTRAMUROS"
  status: "PENDING" | "PUBLISHED" | "FAILED"
}

export type Occurrence = {
  id: number,
  scheduledAt: string,
  status: "SCHEDULED" | "PUBLISHED"
  publication: Publication
  deliveries: Delivery[]
}

export type WeeklyOccurrences = Map<string, Occurrence[]>