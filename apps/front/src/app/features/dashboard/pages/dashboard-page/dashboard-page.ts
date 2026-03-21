import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { finalize } from "rxjs";
import {
  CreatePublicationFormValue,
  CreatePublicationRequest,
  Publication,
} from '../../../publications/publication.model';
import {
  PublicationsService,
} from '../../../publications/publications.service';
import {
  DashboardKpiComponent,
} from "../../components/kpi/dashboard-kpi.component";
import {
  UpcomingPostsComponent,
} from "../../components/upcoming-posts/upcoming-posts.component";

type PublicationsState = {
  publications: Publication[];
  loading: boolean;
  error: string | null;
};

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    DashboardKpiComponent,
    UpcomingPostsComponent,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent implements OnInit {
  private readonly publicationService = inject(PublicationsService);
  private destroyRef = inject(DestroyRef);

  private readonly publicationsState = signal<PublicationsState>({
    publications: [],
    loading: false,
    error: null,
  });

  ngOnInit(): void {
    this.loadPublications()
  }

  private loadPublications() {
    this.publicationsState.update(state => ({
      ...state,
      loading: true,
      error: null,
    }));

    this.publicationService.getPublications()
        .pipe(
          finalize(() => {
            this.publicationsState.update(state => ({
              ...state,
              loading: false,
            }));
          }),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe({
          next: (pubs: Publication[]) => {
            this.publicationsState.update(state => ({
              ...state,
              publications: pubs,
            }));
          },
          error: () => {
            this.publicationsState.update(state => ({
              ...state,
              error: 'Impossible de charger les publications.',
            }));
          },
        });
  }

  protected handleCreatePublication(payload: CreatePublicationFormValue) {
    const newPublication: CreatePublicationRequest = {
      ...payload,
      status: payload.status ? 'VERIFIED' : 'DRAFT',
    }

    this.publicationService.createPublication(newPublication).pipe(
      finalize(() => {
        this.publicationsState.update(state => ({
          ...state,
          loading: false,
        }));
      }),
      takeUntilDestroyed(this.destroyRef),
    )
        .subscribe({
          next: (pub: Publication) => {
            this.publicationsState.update(state => ({
              ...state,
              publications: [...state.publications, pub],
            }));
          },
          error: () => {
            this.publicationsState.update(state => ({
              ...state,
              error: 'Impossible de charger les publications.',
            }));
          },
        });
  }

  protected readonly publications = computed(() => this.publicationsState().publications);
  protected readonly isLoading = computed(() => this.publicationsState().loading);
  protected readonly errorMessage = computed(() => this.publicationsState().error);
  protected readonly isEmpty = computed(() => this.publicationsState().publications.length === 0);
}