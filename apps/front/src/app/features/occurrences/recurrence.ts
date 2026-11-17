/**
 * Choix de répétition de la modale « Programmer », traduits en règle `RRULE`
 * (RFC 5545, le sous-ensemble qu'accepte le back) et en phrase lisible.
 *
 * Les préréglages reprennent ceux des Rappels iOS/macOS ; « Personnaliser »
 * ouvre la fréquence, le « tous les N », les jours de la semaine, les jours du
 * mois (ou « le premier lundi »…) et les mois de l'année.
 */

export type RepeatPreset =
  | 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'WEEKLY' | 'BIWEEKLY'
  | 'MONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'YEARLY' | 'CUSTOM';

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

/** Codes RFC 5545, dans l'ordre de la semaine française. */
export const WEEKDAYS = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'] as const;
export type Weekday = typeof WEEKDAYS[number];

export const WEEKDAY_LABELS: Record<Weekday, string> = {
  MO: 'lundi', TU: 'mardi', WE: 'mercredi', TH: 'jeudi', FR: 'vendredi', SA: 'samedi', SU: 'dimanche',
};

export const MONTH_LABELS = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
];

/** Rangs proposés pour « le premier lundi » ; -1 = le dernier. */
export const ORDINALS = [1, 2, 3, 4, -1] as const;
export const ORDINAL_LABELS: Record<number, string> = {
  1: 'premier', 2: 'deuxième', 3: 'troisième', 4: 'quatrième', [-1]: 'dernier',
};

export const PRESET_LABELS: Record<RepeatPreset, string> = {
  DAILY: 'Chaque jour',
  WEEKDAYS: 'Jours ouvrés (lundi au vendredi)',
  WEEKENDS: 'Week-ends',
  WEEKLY: 'Chaque semaine',
  BIWEEKLY: 'Toutes les 2 semaines',
  MONTHLY: 'Chaque mois',
  QUARTERLY: 'Tous les 3 mois',
  SEMIANNUAL: 'Tous les 6 mois',
  YEARLY: 'Chaque année',
  CUSTOM: 'Personnaliser…',
};

const PRESET_RULES: Record<Exclude<RepeatPreset, 'CUSTOM'>, string> = {
  DAILY: 'FREQ=DAILY',
  WEEKDAYS: 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR',
  WEEKENDS: 'FREQ=WEEKLY;BYDAY=SA,SU',
  WEEKLY: 'FREQ=WEEKLY',
  BIWEEKLY: 'FREQ=WEEKLY;INTERVAL=2',
  MONTHLY: 'FREQ=MONTHLY',
  QUARTERLY: 'FREQ=MONTHLY;INTERVAL=3',
  SEMIANNUAL: 'FREQ=MONTHLY;INTERVAL=6',
  YEARLY: 'FREQ=YEARLY',
};

/** Saisie « Personnaliser ». */
export interface CustomRepeat {
  frequency: Frequency
  interval: number
  /** Hebdomadaire : jours retenus ; vide, le jour de départ. */
  weekdays: Weekday[]
  /** Mensuel : jours du mois (`days`), ou un jour ordinal (`ordinal`). */
  monthMode: 'days' | 'ordinal'
  /** Vide, le quantième du jour de départ. */
  monthDays: number[]
  ordinal: number
  ordinalDay: Weekday
  /** Annuel : mois retenus (1 à 12) ; vide, le mois de départ. */
  months: number[]
}

/** Fin de la répétition. */
export type RepeatEnd =
  | { kind: 'never' }
  | { kind: 'until', date: string }
  | { kind: 'count', count: number };

export function defaultCustomRepeat(): CustomRepeat {
  return {
    frequency: 'WEEKLY', interval: 1, weekdays: [], monthMode: 'days', monthDays: [],
    ordinal: 1, ordinalDay: 'MO', months: [],
  };
}

/** `FREQ=…` prêt pour `POST /api/occurrence-series`. */
export function toRRule(preset: RepeatPreset, custom: CustomRepeat, end: RepeatEnd): string {
  const parts = [preset === 'CUSTOM' ? customRule(custom) : PRESET_RULES[preset]];

  if (end.kind === 'count') {
    parts.push(`COUNT=${ end.count }`);
  } else if (end.kind === 'until' && end.date) {
    parts.push(`UNTIL=${ end.date.replaceAll('-', '') }`);
  }

  return parts.join(';');
}

function customRule(custom: CustomRepeat): string {
  const parts = [`FREQ=${ custom.frequency }`];

  if (custom.interval > 1) {
    parts.push(`INTERVAL=${ custom.interval }`);
  }

  if (custom.frequency === 'WEEKLY' && custom.weekdays.length) {
    parts.push(`BYDAY=${ sortWeekdays(custom.weekdays).join(',') }`);
  }

  if (custom.frequency === 'MONTHLY' || custom.frequency === 'YEARLY') {
    if (custom.frequency === 'YEARLY' && custom.months.length) {
      parts.push(`BYMONTH=${ [...custom.months].sort((a, b) => a - b).join(',') }`);
    }
    if (custom.monthMode === 'ordinal') {
      parts.push(`BYDAY=${ custom.ordinal }${ custom.ordinalDay }`);
    } else if (custom.monthDays.length) {
      parts.push(`BYMONTHDAY=${ [...custom.monthDays].sort((a, b) => a - b).join(',') }`);
    }
  }

  return parts.join(';');
}

/**
 * La répétition en une phrase : « Toutes les 2 semaines, le lundi et le jeudi,
 * à partir du 23/11/2026, 10 fois. » `start` au format `AAAA-MM-JJ`.
 */
export function describeRepeat(preset: RepeatPreset, custom: CustomRepeat, end: RepeatEnd,
                               start: string, time: string): string {
  const what = preset === 'CUSTOM' ? describeCustom(custom) : PRESET_LABELS[preset];
  const parts = [what];

  if (start) {
    parts.push(`à partir du ${ frenchDate(start) }`);
  }
  parts.push(time ? `à ${ time }` : 'heure automatique');

  if (end.kind === 'until' && end.date) {
    parts.push(`jusqu’au ${ frenchDate(end.date) }`);
  } else if (end.kind === 'count') {
    parts.push(`${ end.count } fois`);
  } else {
    parts.push('sans fin');
  }

  return `${ parts.join(', ') }.`;
}

function describeCustom(custom: CustomRepeat): string {
  const n = custom.interval;
  const every: Record<Frequency, string> = {
    DAILY: n > 1 ? `Tous les ${ n } jours` : 'Chaque jour',
    WEEKLY: n > 1 ? `Toutes les ${ n } semaines` : 'Chaque semaine',
    MONTHLY: n > 1 ? `Tous les ${ n } mois` : 'Chaque mois',
    YEARLY: n > 1 ? `Tous les ${ n } ans` : 'Chaque année',
  };
  const parts = [every[custom.frequency]];

  if (custom.frequency === 'WEEKLY' && custom.weekdays.length) {
    parts.push(joinFrench(sortWeekdays(custom.weekdays).map((day) => `le ${ WEEKDAY_LABELS[day] }`)));
  }

  if (custom.frequency === 'YEARLY' && custom.months.length) {
    parts.push(`en ${ joinFrench([...custom.months].sort((a, b) => a - b).map((m) => MONTH_LABELS[m - 1])) }`);
  }

  if (custom.frequency === 'MONTHLY' || custom.frequency === 'YEARLY') {
    if (custom.monthMode === 'ordinal') {
      parts.push(`le ${ ORDINAL_LABELS[custom.ordinal] } ${ WEEKDAY_LABELS[custom.ordinalDay] }`);
    } else if (custom.monthDays.length) {
      const days = [...custom.monthDays].sort((a, b) => a - b).map((d) => `le ${ d === 1 ? '1er' : d }`);
      parts.push(joinFrench(days));
    }
  }

  return parts.join(', ');
}

function sortWeekdays(days: Weekday[]): Weekday[] {
  return WEEKDAYS.filter((day) => days.includes(day));
}

/** « a », « a et b », « a, b et c ». */
function joinFrench(items: string[]): string {
  return items.length <= 1 ? items.join('') : `${ items.slice(0, -1).join(', ') } et ${ items[items.length - 1] }`;
}

function frenchDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${ day }/${ month }/${ year }`;
}

/** Le jour de la semaine d'une date `AAAA-MM-JJ`, en code RFC 5545. */
export function weekdayOf(iso: string): Weekday {
  const [year, month, day] = iso.split('-').map(Number);
  // getDay() : 0 = dimanche.
  return WEEKDAYS[(new Date(year, month - 1, day).getDay() + 6) % 7];
}
