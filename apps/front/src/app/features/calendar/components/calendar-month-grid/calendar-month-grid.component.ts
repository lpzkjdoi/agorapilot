import { NgTemplateOutlet } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  TemplateRef,
  input,
  output,
  signal,
} from '@angular/core';
import { Occurrence } from "../../../occurrences/occurrence.model";
import { isModifiable } from "../../../occurrences/occurrence.utils";
import { CalendarDay, OCCURRENCE_DRAG_TYPE, WEEKDAYS } from "../../calendar.utils";
import {
  CalendarOccurrenceChipComponent,
} from "../calendar-occurrence-chip/calendar-occurrence-chip.component";

/** Au-delà, la case affiche « +N » et renvoie à la bulle du jour. */
const MAX_VISIBLE = 3;

/**
 * Contexte passé au gabarit de la bulle : le jour, et de quel côté l'ouvrir
 * pour qu'elle reste dans la page (vers la gauche en fin de semaine, vers le
 * haut sur les deux dernières semaines).
 */
export interface DayPanelContext {
  $implicit: string
  end: boolean
  up: boolean
}

export interface OccurrenceMove {
  occurrenceId: number
  date: string
}

/**
 * Grille du mois (maquette `/calendar`) : une case par jour, les diffusions en
 * pastilles, la bulle du jour sélectionné.
 *
 * Le glisser-déposer se fait ici : une diffusion modifiable peut atterrir sur un
 * jour du mois qui n'est pas passé. La grille n'appelle rien — elle émet le
 * déplacement, et la page le soumet au back.
 */
@Component({
  selector: 'app-calendar-month-grid',
  imports: [NgTemplateOutlet, CalendarOccurrenceChipComponent],
  templateUrl: './calendar-month-grid.component.html',
  styleUrl: './calendar-month-grid.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarMonthGridComponent {
  readonly days = input.required<CalendarDay[]>();
  readonly occurrencesByDay = input.required<ReadonlyMap<string, Occurrence[]>>();
  /** `2026-10-03` */
  readonly today = input.required<string>();
  readonly now = input.required<Date>();
  readonly selectedDay = input<string | null>(null);

  /** Bulle du jour sélectionné, fournie par la page. */
  readonly panel = input<TemplateRef<DayPanelContext> | null>(null);

  readonly daySelected = output<string>();
  readonly moved = output<OccurrenceMove>();

  protected readonly weekdays = WEEKDAYS;
  protected readonly maxVisible = MAX_VISIBLE;

  /** Case survolée pendant un glisser-déposer. */
  protected readonly dropTarget = signal<string | null>(null);

  protected panelContext(day: CalendarDay, index: number): DayPanelContext {
    const weeks = this.days().length / 7;

    return {
      $implicit: day.date,
      end: index % 7 >= 5,
      up: Math.floor(index / 7) >= weeks - 2,
    };
  }

  protected occurrencesOf(day: CalendarDay): Occurrence[] {
    return this.occurrencesByDay().get(day.date) ?? [];
  }

  protected movable(occurrence: Occurrence): boolean {
    return isModifiable(occurrence, this.now());
  }

  protected select(day: CalendarDay, event?: Event): void {
    // Les clics dans la bulle — y compris sur son fond, qui la ferme —
    // remontent jusqu'à la case : ils ne doivent pas la rouvrir.
    if ((event?.target as Element | null)?.closest('app-calendar-day-panel')) {
      return;
    }

    if (day.inMonth) {
      this.daySelected.emit(day.date);
    }
  }

  protected onDragOver(event: DragEvent, day: CalendarDay): void {
    if (!this.acceptsDrop(event, day)) {
      return;
    }

    // `preventDefault` sur `dragover` : c'est ce qui autorise le dépôt ici.
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dropTarget.set(day.date);
  }

  protected onDragLeave(day: CalendarDay): void {
    if (this.dropTarget() === day.date) {
      this.dropTarget.set(null);
    }
  }

  protected onDrop(event: DragEvent, day: CalendarDay): void {
    this.dropTarget.set(null);

    if (!this.acceptsDrop(event, day)) {
      return;
    }

    event.preventDefault();
    const occurrenceId = Number(event.dataTransfer?.getData(OCCURRENCE_DRAG_TYPE));

    if (occurrenceId) {
      this.moved.emit({ occurrenceId, date: day.date });
    }
  }

  /** Un jour passé ne peut plus accueillir de diffusion ; le back le refuserait. */
  private acceptsDrop(event: DragEvent, day: CalendarDay): boolean {
    const types = Array.from(event.dataTransfer?.types ?? []);

    return types.includes(OCCURRENCE_DRAG_TYPE) && day.inMonth && day.date >= this.today();
  }
}
