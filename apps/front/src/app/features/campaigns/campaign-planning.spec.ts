import { Occurrence, OccurrenceStatus } from '../occurrences/occurrence.model';
import { Publication } from '../publications/publication.model';
import { planningByPublication, planningStats } from './campaign-planning';

function publication(id: number): Publication {
  return { id, content: `Publication ${ id }`, status: 'VERIFIED', medias: [], campaign: null };
}

function occurrence(id: number, publicationId: number, scheduledAt: string, status: OccurrenceStatus): Occurrence {
  return {
    id,
    scheduledAt,
    pinned: false,
    status,
    publication: publication(publicationId),
    deliveries: [{ id, channel: 'FACEBOOK', status: status === 'SCHEDULED' ? 'PENDING' : status }],
  };
}

describe('campaign planning', () => {
  const occurrences = [
    occurrence(1, 8, '2026-12-10T18:30:00', 'SCHEDULED'),
    occurrence(2, 8, '2026-12-05T18:30:00', 'SCHEDULED'),
    occurrence(3, 8, '2026-11-01T12:00:00', 'PUBLISHED'),
    occurrence(4, 8, '2026-11-03T12:00:00', 'FAILED'),
    occurrence(5, 9, '2026-11-02T12:00:00', 'PUBLISHED'),
  ];

  it('should count publications and diffusions by status', () => {
    expect(planningStats([publication(8), publication(9), publication(10)], occurrences)).toEqual({
      publications: 3,
      scheduled: 2,
      published: 2,
      failed: 1,
    });
  });

  it('should keep the nearest scheduled diffusion and the latest settled one', () => {
    const planning = planningByPublication(occurrences).get(8)!;

    expect(planning.next?.id).toBe(2);
    expect(planning.scheduledCount).toBe(2);
    expect(planning.last?.id).toBe(4);
  });

  it('should leave next empty for a publication with nothing left to broadcast', () => {
    const planning = planningByPublication(occurrences).get(9)!;

    expect(planning.next).toBeNull();
    expect(planning.scheduledCount).toBe(0);
    expect(planning.last?.id).toBe(5);
  });

  it('should know nothing about a publication never scheduled', () => {
    expect(planningByPublication(occurrences).has(10)).toBe(false);
  });
});
