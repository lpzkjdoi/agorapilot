import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith } from 'rxjs';
import { PublicationService } from "../publication-service";
import { Publication } from '../types/publication.type';

type PublicationsState = {
  data: Publication[];
  loading: boolean;
  error: string | null;
};

@Component({
  selector: 'app-homepage',
  standalone: true,
  imports: [],
  templateUrl: './homepage.html',
  styleUrl: './homepage.css',
})
export class HomepageComponent {
  private readonly publicationService = inject(PublicationService);

  private readonly publicationsState = toSignal(
    this.publicationService.getPublications().pipe(
      map((publications): PublicationsState => ({
        data: publications,
        loading: false,
        error: null,
      })),
      startWith({
        data: [],
        loading: true,
        error: null,
      }),
      catchError(() =>
        of({
          data: [],
          loading: false,
          error: 'Impossible de charger les publications.',
        }),
      ),
    ),
    {
      initialValue: {
        data: [],
        loading: true,
        error: null,
      },
    },
  );

  readonly publications = computed(() => this.publicationsState().data);
  readonly isLoading = computed(() => this.publicationsState().loading);
  readonly errorMessage = computed(() => this.publicationsState().error);
  readonly isEmpty = computed(() => this.publications().length === 0)
}