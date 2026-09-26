import { formatDate } from "@angular/common";
import { HttpErrorResponse } from "@angular/common/http";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { NotificationService } from "../../../../core/notifications/notification.service";
import { Occurrence } from "../../../occurrences/occurrence.model";
import {
  apiErrorMessage,
  isModifiable,
  scheduledDay,
  scheduledTime,
  toIsoDate,
} from "../../../occurrences/occurrence.utils";
import { OccurrencesService } from "../../../occurrences/occurrences.service";
import {
  CalendarMonth,
  monthGrid,
  monthOf,
  monthRange,
  monthTitle,
  shiftMonth,
} from "../../calendar.utils";
import {
  CalendarDayPanelComponent,
} from "../../components/calendar-day-panel/calendar-day-panel.component";
import {
  EditOccurrenceModalComponent,
  RescheduleIntent,
} from "../../components/edit-occurrence-modal/edit-occurrence-modal.component";
import {
  CalendarMonthGridComponent,
  OccurrenceMove,
} from "../../components/calendar-month-grid/calendar-month-grid.component";

/**
 * Calendrier du mois (maquette `/calendar`) : voir ce qui est programmé,
 * déplacer une diffusion d'un jour à l'autre, fixer son heure ou l'annuler.
 *
 * Tout changement re-répartit, côté back, les autres diffusions des jours
 * touchés : la page recharge le mois après chaque modification plutôt que de
 * deviner les nouveaux horaires.
 */
@Component({
  selector: 'app-calendar-page',
  imports: [
    CalendarMonthGridComponent,
    CalendarDayPanelComponent,
    EditOccurrenceModalComponent,
    LoaderComponent,
  ],
  templateUrl: './calendar-page.component.html',
  styleUrl: './calendar-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarPageComponent {
  private readonly occurrencesService = inject(OccurrencesService);
  private readonly notifications = inject(NotificationService);

  protected readonly month = signal<CalendarMonth>(monthOf(new Date()));
  protected readonly occurrences = signal<Occurrence[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly selectedDay = signal<string | null>(null);
  /** Diffusion ouverte dans la modale de modification. */
  protected readonly editing = signal<Occurrence | null>(null);
  /** Diffusion dont la modification est en vol. */
  protected readonly busyId = signal<number | null>(null);

  /** Rafraîchie à chaque chargement : elle décide de ce qui est encore modifiable. */
  protected readonly now = signal(new Date());
  protected readonly today = computed(() => toIsoDate(this.now()));

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

  /** Numéro du dernier chargement lancé : une réponse plus ancienne est ignorée. */
  private lastLoad = 0;

  protected readonly selectedOccurrences = computed(
    () => this.occurrencesByDay().get(this.selectedDay() ?? '') ?? [],
  );

  constructor() {
    this.load();
  }

  protected previous(): void {
    this.goTo(shiftMonth(this.month(), -1));
  }

  protected next(): void {
    this.goTo(shiftMonth(this.month(), 1));
  }

  protected goToToday(): void {
    this.goTo(monthOf(new Date()));
  }

  /** La bulle se ferme : la modale prend le relais, et la touche Échap ne fermerait qu'elle. */
  protected openEdit(occurrence: Occurrence): void {
    this.selectedDay.set(null);
    this.editing.set(occurrence);
  }

  protected onMoved({ occurrenceId, date }: OccurrenceMove): void {
    const occurrence = this.occurrences().find((candidate) => candidate.id === occurrenceId);

    if (!occurrence || date === scheduledDay(occurrence)) {
      return;
    }

    // Une heure fixée voyage avec la diffusion ; une heure automatique est
    // recalculée dans la fenêtre du jour d'arrivée.
    this.reschedule({ occurrence, date, time: occurrence.pinned ? scheduledTime(occurrence) : null });
  }

  protected reschedule({ occurrence, date, time }: RescheduleIntent): void {
    if (this.busyId() !== null || !isModifiable(occurrence, new Date())) {
      return;
    }

    this.busyId.set(occurrence.id);

    this.occurrencesService.reschedule(occurrence.id, { date, time }).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        const when = formatDate(updated.scheduledAt, "EEEE d MMMM 'à' HH:mm", 'fr-FR');
        this.notifications.success(`Diffusion reprogrammée le ${ when }.`);
        this.editing.set(null);
        this.load(false);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Déplacement impossible', err);
        this.busyId.set(null);
        this.notifications.error(apiErrorMessage(err, 'La diffusion n’a pas pu être déplacée.'));
        // Le refus vient souvent d'un état qui a changé (diffusion partie
        // entre-temps) : la modale se ferme sur un calendrier rechargé.
        this.editing.set(null);
        this.load(false);
      },
    });
  }

  protected cancel(occurrence: Occurrence): void {
    if (this.busyId() !== null) {
      return;
    }

    this.busyId.set(occurrence.id);

    this.occurrencesService.cancel(occurrence.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.notifications.success('Diffusion annulée.');
        this.editing.set(null);
        this.load(false);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Annulation impossible', err);
        this.busyId.set(null);
        this.notifications.error(apiErrorMessage(err, 'La diffusion n’a pas pu être annulée.'));
        this.editing.set(null);
        this.load(false);
      },
    });
  }

  private goTo(month: CalendarMonth): void {
    this.month.set(month);
    this.selectedDay.set(null);
    this.load();
  }

  /**
   * @param showLoader faux pour un rechargement après modification : la grille
   *                   reste en place au lieu de clignoter
   */
  private load(showLoader = true): void {
    const { from, to } = monthRange(this.month());
    const load = ++this.lastLoad;
    this.loading.set(showLoader);
    this.error.set(null);

    this.occurrencesService.getBetween(from, to).subscribe({
      next: (occurrences) => {
        // Changer vite de mois laisse des réponses en route : seule la
        // dernière correspond au mois affiché.
        if (load !== this.lastLoad) {
          return;
        }

        this.now.set(new Date());
        this.occurrences.set(occurrences);
        this.loading.set(false);
      },
      error: (err) => {
        if (load !== this.lastLoad) {
          return;
        }

        console.error('Chargement du calendrier impossible', err);
        this.error.set('Le calendrier n’a pas pu être chargé.');
        this.loading.set(false);
      },
    });
  }
}
