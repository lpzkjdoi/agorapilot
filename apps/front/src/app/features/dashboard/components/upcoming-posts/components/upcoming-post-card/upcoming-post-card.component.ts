import { Component, input } from '@angular/core';
import { Occurrence } from "../../../../../occurrences/occurrence.model";

@Component({
  selector: 'app-upcoming-post-card',
  imports: [],
  templateUrl: './upcoming-post-card.component.html',
  styleUrl: './upcoming-post-card.component.css',
})
export class UpcomingPostCardComponent {
  occurrence = input<Occurrence>()
}
