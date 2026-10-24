import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from "@angular/core/rxjs-interop";
import { catchError, EMPTY, finalize } from "rxjs";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { OccurrencesService } from "../../../occurrences/occurrences.service";
import { DashboardService } from "../../dashboard.service";
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
    LoaderComponent,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent {
  private readonly occurrencesService = inject(OccurrencesService);
  private readonly dashboardService = inject(DashboardService);

  protected readonly loading = signal(true)
  protected readonly summaryLoading = signal(true)

  // En cas d'erreur, les compteurs restent à « – » plutôt que d'afficher des
  // zéros trompeurs.
  protected readonly summary = toSignal(this.dashboardService.getSummary()
                                            .pipe(
                                              catchError((err) => {
                                                console.error('Failed to load dashboard summary', err);
                                                return EMPTY;
                                              }),
                                              finalize(() => this.summaryLoading.set(false))))

  // `finalize` plutôt que `tap` : le loader s'arrête aussi quand le flux se
  // termine sans rien émettre, pas seulement sur une réponse ou une erreur.
  protected readonly weeklyOccurrences = toSignal(this.occurrencesService.getWeeklyOccurrences()
                                                      .pipe(
                                                        catchError((err) => {
                                                          console.error('Failed to load weekly occurrences', err);
                                                          return EMPTY;
                                                        }),
                                                        finalize(() => this.loading.set(false))))
}