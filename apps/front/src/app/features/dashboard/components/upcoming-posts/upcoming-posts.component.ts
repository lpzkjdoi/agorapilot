import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { Occurrence } from "../../../../models/occurrence.model";
import {
  UpcomingPostCardComponent,
} from "./components/upcoming-post-card/upcoming-post-card.component";

@Component({
  selector: 'app-upcoming-posts',
  imports: [
    UpcomingPostCardComponent,
  ],
  templateUrl: './upcoming-posts.component.html',
  styleUrl: './upcoming-posts.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpcomingPostsComponent {
  upcomingOccurrences = input<Occurrence[]>([])

  isEmpty = computed(() => this.upcomingOccurrences().length === 0)
}
