import { AsyncPipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Observable } from "rxjs";
import { WeeklyOccurrences } from "../../../occurrences/occurrence.model";
import { OccurrencesService } from "../../../occurrences/occurrences.service";
import {
  DashboardKpiComponent,
} from "../../components/kpi/dashboard-kpi.component";
import {
  WeeklyCalendarComponent,
} from "../../components/upcoming-posts/components/weekly-calendar/weekly-calendar.component";
import {
  UpcomingPostsComponent,
} from "../../components/upcoming-posts/upcoming-posts.component";

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    DashboardKpiComponent,
    UpcomingPostsComponent,
    WeeklyCalendarComponent,
    AsyncPipe,
  ],
  templateUrl: './dashboard-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent {
  private readonly occurrencesService = inject(OccurrencesService);
  protected readonly weeklyOccurrences$: Observable<WeeklyOccurrences> = this.occurrencesService.getWeeklyOccurrences()
}