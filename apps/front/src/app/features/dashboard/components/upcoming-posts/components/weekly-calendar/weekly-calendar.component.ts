import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {
  WeeklyOccurrenceCardComponent,
} from "../../../../../occurrences/components/weekly-occurrence-card/weekly-occurrence-card.component";
import { WeeklyOccurrences } from "../../../../../occurrences/occurrence.model";

@Component({
  selector: 'app-weekly-calendar',
  imports: [
    WeeklyOccurrenceCardComponent,
  ],
  templateUrl: './weekly-calendar.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WeeklyCalendarComponent {
  weeklyOccurrences = input<WeeklyOccurrences | null>()
  readonly entries = computed<[string, string][]>(() => Object.entries(this.weeklyOccurrences() ?? {}));
}
