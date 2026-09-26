import { formatDate } from "@angular/common";
import { toIsoDate } from "../occurrences/occurrence.utils";

/** Un mois du calendrier ; `month` va de 0 (janvier) à 11, comme `Date`. */
export interface CalendarMonth {
  year: number
  month: number
}

/** Une case de la grille. Les jours hors du mois comblent la première et la dernière semaine. */
export interface CalendarDay {
  /** `2026-10-03` */
  date: string
  dayOfMonth: number
  inMonth: boolean
}

export const WEEKDAYS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

/** Type MIME porté par le glisser-déposer d'une diffusion. */
export const OCCURRENCE_DRAG_TYPE = 'application/x-agorapilot-occurrence';

export function monthOf(date: Date): CalendarMonth {
  return { year: date.getFullYear(), month: date.getMonth() };
}

export function shiftMonth({ year, month }: CalendarMonth, delta: number): CalendarMonth {
  return monthOf(new Date(year, month + delta, 1));
}

/** `Octobre 2026` */
export function monthTitle({ year, month }: CalendarMonth): string {
  const title = formatDate(new Date(year, month, 1), 'MMMM y', 'fr-FR');
  return title.charAt(0).toUpperCase() + title.slice(1);
}

/**
 * Semaines complètes, du lundi au dimanche — l'usage français, là où la
 * maquette (en anglais) commence le dimanche. Quatre à six semaines selon le
 * mois, sans ligne vide superflue.
 */
export function monthGrid({ year, month }: CalendarMonth): CalendarDay[] {
  const first = new Date(year, month, 1);
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const leading = (first.getDay() + 6) % 7;
  const cells = Math.ceil((leading + daysInMonth) / 7) * 7;

  return Array.from({ length: cells }, (_, index) => {
    const date = new Date(year, month, 1 - leading + index);

    return {
      date: toIsoDate(date),
      dayOfMonth: date.getDate(),
      inMonth: date.getMonth() === month,
    };
  });
}

/** Intervalle `[1er du mois, 1er du mois suivant)` demandé au back. */
export function monthRange(calendarMonth: CalendarMonth): { from: string, to: string } {
  const { year, month } = calendarMonth;

  return {
    from: toIsoDate(new Date(year, month, 1)),
    to: toIsoDate(new Date(year, month + 1, 1)),
  };
}
