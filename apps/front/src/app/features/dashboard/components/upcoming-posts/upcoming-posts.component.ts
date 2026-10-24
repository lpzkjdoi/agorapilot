import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from "@angular/router";
import { Occurrence } from "../../../occurrences/occurrence.model";
import {
  UpcomingPostCardComponent,
} from "./components/upcoming-post-card/upcoming-post-card.component";

@Component({
  selector: 'app-upcoming-posts',
  imports: [
    RouterLink,
    UpcomingPostCardComponent,
  ],
  templateUrl: './upcoming-posts.component.html',
  styleUrl: './upcoming-posts.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpcomingPostsComponent {
  upcomingOccurrences = input<Occurrence[]>([])
  /** Tant que la liste charge, « Aucune publication prévue » serait faux. */
  loading = input(false)
}
