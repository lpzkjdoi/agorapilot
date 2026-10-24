import { Occurrence } from "../occurrences/occurrence.model";

/** Reflète `DashboardDTO` côté back (`GET /api/dashboard`). */
export interface DashboardSummary {
  /** Campagnes en cours : commencées, ni finies, ni clôturées. */
  activeCampaigns: number
  /** Diffusions encore programmées sur les sept jours du calendrier de la semaine. */
  scheduledThisWeek: number
  /** Publications en brouillon, à vérifier avant diffusion. */
  draftPublications: number
  /** Diffusions en échec, en attente d'une reprise. */
  failedOccurrences: number
  /** Les cinq prochaines diffusions programmées, la plus proche d'abord. */
  upcomingOccurrences: Occurrence[]
}
