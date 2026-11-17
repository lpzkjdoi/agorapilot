import {
  CustomRepeat,
  defaultCustomRepeat,
  describeRepeat,
  RepeatEnd,
  RepeatPreset,
  toRRule,
  weekdayOf,
} from './recurrence';

const never: RepeatEnd = { kind: 'never' };

function custom(overrides: Partial<CustomRepeat>): CustomRepeat {
  return { ...defaultCustomRepeat(), ...overrides };
}

describe('recurrence', () => {
  describe('toRRule', () => {
    it.each<[RepeatPreset, string]>([
      ['DAILY', 'FREQ=DAILY'],
      ['WEEKDAYS', 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR'],
      ['WEEKENDS', 'FREQ=WEEKLY;BYDAY=SA,SU'],
      ['WEEKLY', 'FREQ=WEEKLY'],
      ['BIWEEKLY', 'FREQ=WEEKLY;INTERVAL=2'],
      ['MONTHLY', 'FREQ=MONTHLY'],
      ['QUARTERLY', 'FREQ=MONTHLY;INTERVAL=3'],
      ['SEMIANNUAL', 'FREQ=MONTHLY;INTERVAL=6'],
      ['YEARLY', 'FREQ=YEARLY'],
    ])('should translate the preset %s', (preset, rule) => {
      expect(toRRule(preset, defaultCustomRepeat(), never)).toBe(rule);
    });

    it('should add the end of the repetition', () => {
      expect(toRRule('WEEKLY', defaultCustomRepeat(), { kind: 'until', date: '2026-12-31' }))
        .toBe('FREQ=WEEKLY;UNTIL=20261231');
      expect(toRRule('WEEKLY', defaultCustomRepeat(), { kind: 'count', count: 6 })).toBe('FREQ=WEEKLY;COUNT=6');
    });

    it('should build a custom weekly rule with its days in week order', () => {
      expect(toRRule('CUSTOM', custom({ interval: 3, weekdays: ['TH', 'MO'] }), never))
        .toBe('FREQ=WEEKLY;INTERVAL=3;BYDAY=MO,TH');
    });

    it('should build monthly rules on days of the month or on an ordinal weekday', () => {
      expect(toRRule('CUSTOM', custom({ frequency: 'MONTHLY', monthDays: [15, 1] }), never))
        .toBe('FREQ=MONTHLY;BYMONTHDAY=1,15');
      expect(toRRule('CUSTOM', custom({ frequency: 'MONTHLY', monthMode: 'ordinal', ordinal: -1, ordinalDay: 'FR' }), never))
        .toBe('FREQ=MONTHLY;BYDAY=-1FR');
    });

    it('should build a yearly rule on chosen months', () => {
      expect(toRRule('CUSTOM', custom({ frequency: 'YEARLY', months: [6, 1], monthDays: [1] }), never))
        .toBe('FREQ=YEARLY;BYMONTH=1,6;BYMONTHDAY=1');
    });

    it('should ignore the week days of a non-weekly custom rule', () => {
      expect(toRRule('CUSTOM', custom({ frequency: 'DAILY', interval: 2, weekdays: ['MO'] }), never))
        .toBe('FREQ=DAILY;INTERVAL=2');
    });
  });

  describe('describeRepeat', () => {
    it('should sum up a preset with its start, time and end', () => {
      expect(describeRepeat('BIWEEKLY', defaultCustomRepeat(), { kind: 'until', date: '2026-12-31' }, '2026-11-23', '18:00'))
        .toBe('Toutes les 2 semaines, à partir du 23/11/2026, à 18:00, jusqu’au 31/12/2026.');
    });

    it('should sum up a custom rule in French', () => {
      expect(describeRepeat('CUSTOM', custom({ interval: 2, weekdays: ['TH', 'MO'] }), { kind: 'count', count: 10 },
        '2026-11-23', '')).toBe('Toutes les 2 semaines, le lundi et le jeudi, à partir du 23/11/2026, heure automatique, 10 fois.');
      expect(describeRepeat('CUSTOM', custom({ frequency: 'MONTHLY', monthMode: 'ordinal', ordinal: 1, ordinalDay: 'MO' }),
        never, '', '')).toBe('Chaque mois, le premier lundi, heure automatique, sans fin.');
      expect(describeRepeat('CUSTOM', custom({ frequency: 'MONTHLY', interval: 3, monthDays: [15, 1] }), never, '', ''))
        .toBe('Tous les 3 mois, le 1er et le 15, heure automatique, sans fin.');
    });
  });

  it('should give the RFC code of a date’s weekday', () => {
    expect(weekdayOf('2026-11-23')).toBe('MO');
    expect(weekdayOf('2026-11-29')).toBe('SU');
  });
});
