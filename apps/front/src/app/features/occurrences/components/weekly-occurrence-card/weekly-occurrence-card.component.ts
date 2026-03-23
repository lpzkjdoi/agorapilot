import { DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

@Component({
  selector: 'app-weekly-occurrence-card',
  imports: [DatePipe],
  templateUrl: './weekly-occurrence-card.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WeeklyOccurrenceCardComponent {
  entry = input.required<[string, string]>()
  date = computed(() => this.entry()[0])
  numberOfPosts = computed(() => this.entry()[1].length)
  hasPosts = computed(() => this.entry()[1].length > 0)
}
