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
  /** Pseudo de qui l'a programmée ; absent pour une diffusion antérieure à la traçabilité. */
  createdBy?: string | null
  /**
   * Passée son heure mais retenue par la suspension d'un canal : toujours
   * `SCHEDULED`, elle partira à la levée ou passera en échec au-delà du retard
   * maximal. En attendant, elle se déplace et s'annule.
   */
  held?: boolean
  /** Série qui l'a créée (programmation par fréquence) ; absent ou nul sinon. */
  seriesId?: number | null
}

export type SeriesStatus = 'ACTIVE' | 'COMPLETED' | 'STOPPED';

/** Reflète `OccurrenceSeriesDTO` (`/api/occurrence-series`). */
export interface OccurrenceSeries {
  id: number
  publicationId: number
  /** `RRULE` normalisée, sans `DTSTART` : `FREQ=WEEKLY;INTERVAL=2`. */
  rule: string
  startDate: string
  /** `HH:mm:ss`, ou nulle pour une heure automatique. */
  publishTime: string | null
  channels: DeliveryChannel[]
  status: SeriesStatus
  stopReason: string | null
  /** Diffusions de la série encore programmées. */
  upcoming: number
  createdBy?: string | null
  createdAt?: string | null
}

/** Corps de `POST /api/occurrence-series`. `time` absent : heure automatique. */
export interface CreateSeriesRequest {
  publicationId: number
  channels: DeliveryChannel[]
  rule: string
  startDate: string
  time: string | null
}

/** Saisie « Répéter » de la modale « Programmer ». */
export interface RepeatFormValue {
  startDate: string
  /** Vide : heure automatique. */
  time: string
  rule: string
  channels: DeliveryChannel[]
  /** La répétition en une phrase, reprise dans l'annonce de succès. */
  summary: string
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

/** Une date de `POST /api/occurrences/batch`, sous les deux mêmes formes exclusives. */
export type OccurrenceSlot = { date: string } | { scheduledAt: string };

/**
 * Corps de `POST /api/occurrences/batch` : la même publication à plusieurs
 * dates, sur les mêmes canaux. Tout ou rien côté back.
 */
export interface CreateOccurrencesRequest {
  publicationId: number
  channels: DeliveryChannel[]
  slots: OccurrenceSlot[]
}

/**
 * Corps de `PUT /api/occurrences/{id}/schedule` et de
 * `POST /api/occurrences/{id}/retry`. `time` à `null` : l'heure est
 * automatique ; renseignée (`HH:mm`), elle est épinglée.
 */
export interface RescheduleOccurrenceRequest {
  date: string
  time: string | null
}

/** Une date saisie dans la modale « Programmer » ; `time` vide = heure automatique. */
export interface ScheduleSlotValue {
  date: string
  time: string
}

/** Saisie de la modale « Programmer » : une ou plusieurs dates, dans l'ordre chronologique. */
export interface ScheduleFormValue {
  slots: ScheduleSlotValue[]
  channels: DeliveryChannel[]
}
