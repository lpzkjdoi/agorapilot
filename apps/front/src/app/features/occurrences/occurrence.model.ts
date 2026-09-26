import { DeliveryChannel, DeliveryStatus, Publication } from "../publications/publication.model";

/** Livraison d'une occurrence, telle que l'expose `PublicationOccurrenceDTO`. */
export interface OccurrenceDelivery {
  id: number,
  channel: DeliveryChannel
  status: DeliveryStatus
  /** Motif de l'échec quand `status` vaut `FAILED`. */
  errorMessage?: string | null
}

/**
 * Cycle de vie d'une occurrence : `SCHEDULED` tant qu'un canal reste à servir,
 * puis `PUBLISHED` ou `FAILED` une fois tous les canaux tranchés.
 */
export type OccurrenceStatus = "SCHEDULED" | "PUBLISHED" | "FAILED";

export interface Occurrence {
  id: number,
  /** Heure locale du serveur, sans fuseau (`2026-10-03T18:30:00`). */
  scheduledAt: string,
  /** Heure fixée à la main ; sinon placée automatiquement dans la fenêtre du jour. */
  pinned: boolean,
  status: OccurrenceStatus
  publication: Publication
  deliveries: OccurrenceDelivery[]
}

export type WeeklyOccurrences = Map<string, Occurrence[]>

/**
 * Corps de `POST /api/occurrences`, sous l'une de deux formes exclusives :
 * `date` seule (heure choisie par le back dans la fenêtre de publication du
 * jour), ou `scheduledAt` (heure précise, épinglée).
 */
export type CreateOccurrenceRequest =
  | { publicationId: number, channels: DeliveryChannel[], date: string }
  | { publicationId: number, channels: DeliveryChannel[], scheduledAt: string };

/**
 * Corps de `PUT /api/occurrences/{id}/schedule` et de
 * `POST /api/occurrences/{id}/retry`. `time` à `null` : l'heure est
 * automatique ; renseignée (`HH:mm`), elle est épinglée.
 */
export interface RescheduleOccurrenceRequest {
  date: string
  time: string | null
}

/** Saisie de la modale « Programmer » ; `time` vide = heure automatique. */
export interface ScheduleFormValue {
  date: string
  time: string
  channels: DeliveryChannel[]
}
