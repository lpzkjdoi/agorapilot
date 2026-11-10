import { Occurrence } from "../occurrences/occurrence.model";
import { Publication } from "../publications/publication.model";

/** Compteurs de diffusions affichés en tête du détail d'une campagne. */
export interface CampaignPlanningStats {
  publications: number
  scheduled: number
  published: number
  failed: number
}

/** Ce que le détail montre de la planification d'une publication. */
export interface PublicationPlanning {
  /** Prochaine diffusion programmée, la plus proche. */
  next: Occurrence | null
  /** Diffusions encore programmées, la prochaine comprise. */
  scheduledCount: number
  /** Dernière diffusion tranchée (publiée ou en échec), la plus récente. */
  last: Occurrence | null
}

export function planningStats(publications: Publication[], occurrences: Occurrence[]): CampaignPlanningStats {
  return {
    publications: publications.length,
    scheduled: occurrences.filter((occurrence) => occurrence.status === 'SCHEDULED').length,
    published: occurrences.filter((occurrence) => occurrence.status === 'PUBLISHED').length,
    failed: occurrences.filter((occurrence) => occurrence.status === 'FAILED').length,
  };
}

/**
 * Planification de chaque publication, indexée par son identifiant. Les
 * occurrences arrivent du back dans l'ordre chronologique ; l'ordre est
 * malgré tout rétabli ici pour ne pas en dépendre.
 */
export function planningByPublication(occurrences: Occurrence[]): Map<number, PublicationPlanning> {
  const sorted = [...occurrences].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
  const plannings = new Map<number, PublicationPlanning>();

  for (const occurrence of sorted) {
    const planning = plannings.get(occurrence.publication.id) ?? { next: null, scheduledCount: 0, last: null };

    if (occurrence.status === 'SCHEDULED') {
      planning.next ??= occurrence;
      planning.scheduledCount++;
    } else {
      planning.last = occurrence;
    }

    plannings.set(occurrence.publication.id, planning);
  }

  return plannings;
}
