import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from "@angular/core/rxjs-interop";
import { catchError, EMPTY, tap } from "rxjs";
import {
  NotificationsService,
} from "../../../../core/notifications/notifications.service";
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
  ],
  templateUrl: './dashboard-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent {
  private readonly occurrencesService = inject(OccurrencesService);
  private readonly notificationsService = inject(NotificationsService)

  protected readonly loading = signal(true)

  protected readonly weeklyOccurrences = toSignal(this.occurrencesService.getWeeklyOccurrences()
                                                      .pipe(
                                                        tap(() => this.loading.set(false)),
                                                        catchError((err) => {
                                                          this.loading.set(false);
                                                          this.notificationsService.send(err);
                                                          return EMPTY;
                                                        })))
}