import { DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  output,
} from '@angular/core';
import { DeliveryChannel } from "../../../publications/publication.model";
import { Occurrence, OccurrenceStatus } from "../../../occurrences/occurrence.model";
import { failureReason, isModifiable, scheduledTime } from "../../../occurrences/occurrence.utils";

export const STATUS_LABELS: Record<OccurrenceStatus, string> = {
  SCHEDULED: 'Programmée',
  PUBLISHED: 'Publiée',
  FAILED: 'Échec',
};

const CHANNEL_LABELS: Record<DeliveryChannel, string> = {
  FACEBOOK: 'Facebook',
  INTRAMUROS: 'Intramuros',
};

/**
 * Bulle d'un jour du calendrier (maquette : popover au clic sur un jour) : les
 * diffusions du jour avec leur canal, leur statut et leur heure.
 *
 * Elle reste une liste compacte, comme dans la maquette ; la modification passe
 * par « Modifier », qui ouvre une modale.
 */
@Component({
  selector: 'app-calendar-day-panel',
  imports: [DatePipe],
  templateUrl: './calendar-day-panel.component.html',
  styleUrl: './calendar-day-panel.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarDayPanelComponent {
  /** Le jour affiché, `2026-10-03`. */
  readonly date = input.required<string>();
  readonly occurrences = input.required<Occurrence[]>();
  readonly now = input.required<Date>();

  readonly closed = output<void>();
  readonly edit = output<Occurrence>();

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly channelLabels = CHANNEL_LABELS;
  protected readonly time = scheduledTime;
  protected readonly reason = failureReason;

  protected modifiable(occurrence: Occurrence): boolean {
    return isModifiable(occurrence, this.now());
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }
}
