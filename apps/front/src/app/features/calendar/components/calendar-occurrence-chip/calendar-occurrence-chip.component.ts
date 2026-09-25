import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { Occurrence } from "../../../occurrences/occurrence.model";
import { failureReason, scheduledTime } from "../../../occurrences/occurrence.utils";
import { OCCURRENCE_DRAG_TYPE } from "../../calendar.utils";

const STATUS_LABELS = {
  SCHEDULED: 'programmée',
  PUBLISHED: 'publiée',
  FAILED: 'en échec',
} as const;

/**
 * Pastille d'une diffusion dans une case du calendrier : heure, début du
 * contenu, teinte du canal (maquette : bleu Facebook, violet Intramuros).
 *
 * Seules les diffusions encore modifiables se font glisser ; l'identifiant voyage
 * dans le `dataTransfer`, la grille décide où il peut atterrir.
 */
@Component({
  selector: 'app-calendar-occurrence-chip',
  templateUrl: './calendar-occurrence-chip.component.html',
  styleUrl: './calendar-occurrence-chip.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.draggable]': 'draggable() ? "true" : null',
    '[class.chip-draggable]': 'draggable()',
    '(dragstart)': 'onDragStart($event)',
  },
})
export class CalendarOccurrenceChipComponent {
  readonly occurrence = input.required<Occurrence>();
  readonly draggable = input(false);

  readonly time = computed(() => scheduledTime(this.occurrence()));

  /** Une diffusion multi-canal prend la teinte de son premier canal. */
  readonly channel = computed(() => this.occurrence().deliveries[0]?.channel ?? 'FACEBOOK');

  /** Infobulle : le contenu complet, le statut, et le motif d'un échec. */
  readonly tooltip = computed(() => {
    const occurrence = this.occurrence();
    const reason = failureReason(occurrence);
    const how = occurrence.pinned ? 'heure fixe' : 'heure automatique';

    return [
      `${ this.time() } — ${ occurrence.publication.content }`,
      `Diffusion ${ STATUS_LABELS[occurrence.status] } (${ how })`,
      reason ? `Motif : ${ reason }` : null,
    ].filter(Boolean).join('\n');
  });

  onDragStart(event: DragEvent): void {
    if (!this.draggable() || !event.dataTransfer) {
      return;
    }

    event.dataTransfer.setData(OCCURRENCE_DRAG_TYPE, String(this.occurrence().id));
    event.dataTransfer.effectAllowed = 'move';
  }
}
