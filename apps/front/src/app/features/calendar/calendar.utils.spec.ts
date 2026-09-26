import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { monthGrid, monthOf, monthRange, monthTitle, shiftMonth } from './calendar.utils';

registerLocaleData(localeFr, 'fr-FR');

describe('calendar utils', () => {
  it('should title the month in French, capitalised', () => {
    expect(monthTitle({ year: 2026, month: 9 })).toBe('Octobre 2026');
  });

  it('should move across years', () => {
    expect(shiftMonth({ year: 2026, month: 11 }, 1)).toEqual({ year: 2027, month: 0 });
    expect(shiftMonth({ year: 2026, month: 0 }, -1)).toEqual({ year: 2025, month: 11 });
  });

  it('should read the month of a date', () => {
    expect(monthOf(new Date(2026, 9, 25))).toEqual({ year: 2026, month: 9 });
  });

  describe('monthGrid', () => {
    // Octobre 2026 commence un jeudi et finit un samedi.
    const october = monthGrid({ year: 2026, month: 9 });

    it('should start on the Monday of the first week', () => {
      expect(october[0]).toEqual({ date: '2026-09-28', dayOfMonth: 28, inMonth: false });
      expect(october[3]).toEqual({ date: '2026-10-01', dayOfMonth: 1, inMonth: true });
    });

    it('should end on the Sunday of the last week, in whole weeks', () => {
      expect(october.length % 7).toBe(0);
      expect(october.at(-1)).toEqual({ date: '2026-11-01', dayOfMonth: 1, inMonth: false });
    });

    it('should hold every day of the month once', () => {
      expect(october.filter((day) => day.inMonth)).toHaveLength(31);
    });

    it('should not add an empty week to a month that fits in four', () => {
      // Février 2027 commence un lundi et compte 28 jours.
      expect(monthGrid({ year: 2027, month: 1 })).toHaveLength(28);
    });
  });

  it('should request the month as a half-open range', () => {
    expect(monthRange({ year: 2026, month: 11 })).toEqual({ from: '2026-12-01', to: '2027-01-01' });
  });
});
