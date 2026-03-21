import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Occurrence } from "../../../occurrences/occurrence.model";
import {
  UpcomingPostCardComponent,
} from "./components/upcoming-post-card/upcoming-post-card.component";

@Component({
  selector: 'app-upcoming-posts',
  imports: [
    UpcomingPostCardComponent,
  ],
  templateUrl: './upcoming-posts.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpcomingPostsComponent {
  upcomingOccurrences = input<Occurrence[]>([])
}
