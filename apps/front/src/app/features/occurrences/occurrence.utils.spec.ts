import { HttpErrorResponse } from "@angular/common/http";
import { Occurrence } from "./occurrence.model";
import {
  apiErrorMessage,
  failureReason,
  isModifiable,
  scheduledDay,
  scheduledTime,
  toCreateOccurrenceRequest,
  toIsoDate,
} from "./occurrence.utils";

function occurrence(overrides: Partial<Occurrence> = {}): Occurrence {
  return {
    id: 1,
    scheduledAt: '2026-10-03T18:30:00',
    pinned: false,
    status: 'SCHEDULED',
    publication: { id: 10, content: 'Marché de Noël', status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'PENDING' }],
    ...overrides,
  };
}

describe('occurrence utils', () => {
  it('should read the day and time straight from the server time', () => {
    expect(scheduledDay(occurrence())).toBe('2026-10-03');
    expect(scheduledTime(occurrence())).toBe('18:30');
  });

  it('should format a local date without going through UTC', () => {
    // 00:30 heure locale : `toISOString()` renverrait la veille à l'est de Greenwich.
    expect(toIsoDate(new Date(2026, 9, 3, 0, 30))).toBe('2026-10-03');
  });

  describe('isModifiable', () => {
    const now = new Date(2026, 9, 3, 12, 0);

    it('should allow a pending occurrence well ahead of its time', () => {
      expect(isModifiable(occurrence(), now)).toBe(true);
    });

    it('should refuse an imminent occurrence, which the scheduler may pick up any moment', () => {
      expect(isModifiable(occurrence({ scheduledAt: '2026-10-03T12:04:00' }), now)).toBe(false);
      expect(isModifiable(occurrence({ scheduledAt: '2026-10-03T12:06:00' }), now)).toBe(true);
    });

    it('should refuse an occurrence already served or failed', () => {
      expect(isModifiable(occurrence({ status: 'PUBLISHED' }), now)).toBe(false);
      expect(isModifiable(occurrence({ status: 'FAILED' }), now)).toBe(false);
    });

    it('should refuse an occurrence whose delivery is already in progress', () => {
      expect(isModifiable(
        occurrence({ deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'IN_PROGRESS' }] }),
        now,
      )).toBe(false);
    });
  });

  it('should expose the reason of the first failed channel', () => {
    expect(failureReason(occurrence())).toBeNull();
    expect(failureReason(occurrence({
      status: 'FAILED',
      deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'FAILED', errorMessage: 'Token expired' }],
    }))).toBe('Token expired');
  });

  describe('toCreateOccurrenceRequest', () => {
    it('should send the day alone when no time is given', () => {
      expect(toCreateOccurrenceRequest(7, { date: '2026-10-03', time: '', channels: ['FACEBOOK'] }))
        .toEqual({ publicationId: 7, channels: ['FACEBOOK'], date: '2026-10-03' });
    });

    it('should send a precise, pinned time when one is given', () => {
      expect(toCreateOccurrenceRequest(7, { date: '2026-10-03', time: '17:45', channels: ['FACEBOOK'] }))
        .toEqual({ publicationId: 7, channels: ['FACEBOOK'], scheduledAt: '2026-10-03T17:45:00' });
    });
  });

  it('should append the reason given by the back to the fallback message', () => {
    const withReason = new HttpErrorResponse({ status: 400, error: { message: 'La fenêtre est passée.' } });
    const without = new HttpErrorResponse({ status: 500, error: 'boom' });

    expect(apiErrorMessage(withReason, 'Programmation impossible.')).toBe('Programmation impossible. La fenêtre est passée.');
    expect(apiErrorMessage(without, 'Programmation impossible.')).toBe('Programmation impossible.');
  });
});
