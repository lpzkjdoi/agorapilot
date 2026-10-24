import { DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { STATUS_LABELS } from "../../../../../calendar/components/calendar-day-panel/calendar-day-panel.component";
import { Occurrence } from "../../../../../occurrences/occurrence.model";
import { scheduledTime } from "../../../../../occurrences/occurrence.utils";
import { DeliveryChannel } from "../../../../../publications/publication.model";

const CHANNEL_LABELS: Record<DeliveryChannel, string> = {
  FACEBOOK: 'Facebook',
  INTRAMUROS: 'Intramuros',
};

/**
 * Une diffusion à venir (maquette : « Upcoming Posts ») : bloc date, texte de
 * la publication, canaux et statut.
 */
@Component({
  selector: 'app-upcoming-post-card',
  imports: [DatePipe],
  templateUrl: './upcoming-post-card.component.html',
  styleUrl: './upcoming-post-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpcomingPostCardComponent {
  occurrence = input<Occurrence>()

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly channelLabels = CHANNEL_LABELS;
  protected readonly time = computed(() => {
    const occurrence = this.occurrence();
    return occurrence ? scheduledTime(occurrence) : '';
  });
}
