import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  CalendarDayPanelComponent,
} from "../../../calendar/components/calendar-day-panel/calendar-day-panel.component";
import {
  CalendarMonthGridComponent,
  CalendarPeriod,
} from "../../../calendar/components/calendar-month-grid/calendar-month-grid.component";
import {
  CalendarMonth,
  monthGrid,
  monthOf,
  monthTitle,
  shiftMonth,
} from "../../../calendar/calendar.utils";
import { Occurrence } from "../../../occurrences/occurrence.model";
import { scheduledDay, toIsoDate } from "../../../occurrences/occurrence.utils";
import { Campaign } from "../../campaign.model";

/** `2026-12-05` → décembre 2026. */
function monthOfDay(day: string): CalendarMonth {
  return { year: Number(day.slice(0, 4)), month: Number(day.slice(5, 7)) - 1 };
}

/**
 * Le mois qui a le plus de sens à l'ouverture : celui d'aujourd'hui pendant
 * la campagne, sinon celui de son début (à venir) ou de sa fin (passée).
 */
export function initialMonth(period: CalendarPeriod, today: string): CalendarMonth {
  if (period.start && today < period.start) {
    return monthOfDay(period.start);
  }

  if (period.end && today > period.end) {
    return monthOfDay(period.end);
  }

  return monthOfDay(today);
}

/**
 * Calendrier d'une campagne : la grille du mois de la page Calendrier, réduite
 * aux diffusions de la campagne, sa période mise en valeur. En lecture seule —
 * déplacer ou reprendre une diffusion se fait depuis le Calendrier général.
 */
@Component({
  selector: 'app-campaign-calendar',
  imports: [CalendarMonthGridComponent, CalendarDayPanelComponent, DatePipe, RouterLink],
  templateUrl: './campaign-calendar.component.html',
  styleUrl: './campaign-calendar.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignCalendarComponent {
  readonly campaign = input.required<Campaign>();
  readonly occurrences = input.required<Occurrence[]>();
  /** Injectable pour les tests : décide d'aujourd'hui et du mois d'ouverture. */
  readonly now = input<Date>(new Date());

  protected readonly today = computed(() => toIsoDate(this.now()));

  protected readonly period = computed<CalendarPeriod>(() => ({
    start: this.campaign().startDate?.slice(0, 10) ?? null,
    end: this.campaign().endDate?.slice(0, 10) ?? null,
  }));

  /** Rouvert sur le mois d'ouverture à chaque changement de campagne. */
  protected readonly month = linkedSignal<number, CalendarMonth>({
    source: () => this.campaign().id,
    computation: () => initialMonth(this.period(), this.today()),
  });

  protected readonly selectedDay = signal<string | null>(null);

  protected readonly title = computed(() => monthTitle(this.month()));
  protected readonly days = computed(() => monthGrid(this.month()));

  protected readonly occurrencesByDay = computed(() => {
    const byDay = new Map<string, Occurrence[]>();

    for (const occurrence of this.occurrences()) {
      const day = scheduledDay(occurrence);
      byDay.set(day, [...(byDay.get(day) ?? []), occurrence]);
    }

    return byDay;
  });

  protected readonly selectedOccurrences = computed(
    () => this.occurrencesByDay().get(this.selectedDay() ?? '') ?? [],
  );

  /** Diffusions tombées hors de la période : le plus souvent, un oubli à corriger. */
  protected readonly outOfPeriod = computed(() => {
    const { start, end } = this.period();

    return this.occurrences().filter((occurrence) => {
      const day = scheduledDay(occurrence);
      return (start !== null && day < start) || (end !== null && day > end);
    });
  });

  protected previous(): void {
    this.goTo(shiftMonth(this.month(), -1));
  }

  protected next(): void {
    this.goTo(shiftMonth(this.month(), 1));
  }

  protected goToStart(): void {
    const start = this.period().start;
    if (start) {
      this.goTo(monthOfDay(start));
    }
  }

  protected goToToday(): void {
    this.goTo(monthOf(this.now()));
  }

  private goTo(month: CalendarMonth): void {
    this.selectedDay.set(null);
    this.month.set(month);
  }
}
