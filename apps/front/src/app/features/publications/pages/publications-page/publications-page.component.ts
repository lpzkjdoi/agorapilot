import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Observable } from "rxjs";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { NotificationService } from "../../../../core/notifications/notification.service";
import { Campaign } from "../../../campaigns/campaign.model";
import { CampaignsService } from "../../../campaigns/campaigns.service";
import {
  CreatePublicationModalComponent,
} from "../../components/create-publication-modal/create-publication-modal.component";
import {
  PublicationCardComponent,
} from "../../components/publication-card/publication-card.component";
import {
  PublicationFiltersComponent,
  PublicationStats,
} from "../../components/publication-filters/publication-filters.component";
import {
  CreatePublicationFormValue,
  Publication,
  PublicationCampaignFilter,
  PublicationStatusFilter,
} from "../../publication.model";
import { PublicationsService } from "../../publications.service";

@Component({
  selector: 'app-publications-page',
  imports: [
    PublicationCardComponent,
    PublicationFiltersComponent,
    CreatePublicationModalComponent,
    LoaderComponent,
  ],
  templateUrl: './publications-page.component.html',
  styleUrl: './publications-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationsPageComponent {
  private readonly publicationsService = inject(PublicationsService);
  private readonly campaignsService = inject(CampaignsService);
  private readonly notifications = inject(NotificationService);

  protected readonly publications = signal<Publication[]>([]);
  protected readonly campaigns = signal<Campaign[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly createModalOpen = signal(false);
  protected readonly creating = signal(false);

  /** Ids des publications dont la diffusion Facebook est en cours. */
  protected readonly publishing = signal<ReadonlySet<number>>(new Set());

  protected readonly search = signal('');
  protected readonly statusFilter = signal<PublicationStatusFilter>('ALL');
  protected readonly campaignFilter = signal<PublicationCampaignFilter>('ALL');

  protected readonly stats = computed<PublicationStats>(() => {
    const publications = this.publications();

    return {
      total: publications.length,
      verified: publications.filter((publication) => publication.status === 'VERIFIED').length,
      drafts: publications.filter((publication) => publication.status === 'DRAFT').length,
      inCampaigns: publications.filter((publication) => !!publication.campaign).length,
    };
  });

  protected readonly filteredPublications = computed(() => {
    const search = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    const campaign = this.campaignFilter();

    return this.publications().filter((publication) => {
      if (search && !publication.content.toLowerCase().includes(search)) {
        return false;
      }

      if (status !== 'ALL' && publication.status !== status) {
        return false;
      }

      if (campaign === 'NONE') {
        return !publication.campaign;
      }

      if (campaign !== 'ALL') {
        return publication.campaign?.id === campaign;
      }

      return true;
    });
  });

  constructor() {
    this.loadPublications();

    // Les campagnes alimentent le filtre « Toutes les campagnes ».
    this.campaignsService.getCampaigns().subscribe({
      next: (campaigns) => this.campaigns.set(campaigns),
      error: (err) => console.error('Chargement des campagnes impossible', err),
    });
  }

  protected onCreate(value: CreatePublicationFormValue): void {
    this.creating.set(true);

    this.publicationsService
        .createPublication({
          content: value.content,
          status: value.status ? 'VERIFIED' : 'DRAFT',
        })
        .subscribe({
          next: (publication) => {
            this.publications.update((publications) => [publication, ...publications]);
            this.creating.set(false);
            this.createModalOpen.set(false);
            this.notifications.success('Publication créée.');
          },
          error: (err) => {
            console.error('Création de la publication impossible', err);
            this.creating.set(false);
            this.notifications.error('La publication n’a pas pu être créée.');
          },
        });
  }

  protected onGenerateXlsx(publication: Publication): void {
    this.runUnavailableAction(this.publicationsService.generateXlsx(publication.id));
  }

  protected onPublishOnFacebook(publication: Publication): void {
    if (this.publishing().has(publication.id)) {
      return;
    }

    this.setPublishing(publication.id, true);

    this.publicationsService.publishOnFacebook(publication.id).subscribe({
      next: () => {
        this.setPublishing(publication.id, false);
        this.notifications.success('Publication diffusée sur Facebook.');
      },
      error: (err: HttpErrorResponse) => {
        console.error('Diffusion Facebook impossible', err);
        this.setPublishing(publication.id, false);
        this.notifications.error(this.deliveryErrorMessage(err));
      },
    });
  }

  protected onAssignCampaign(publication: Publication): void {
    // Le choix de la campagne se fera dans une modale dédiée, livrée en même
    // temps que l'endpoint de rattachement. En attendant, on déclenche l'appel
    // pour relayer l'indisponibilité plutôt que de simuler un succès.
    this.runUnavailableAction(
      this.publicationsService.assignToCampaign(publication.id, publication.campaign?.id ?? null),
    );
  }

  private loadPublications(): void {
    this.loading.set(true);

    this.publicationsService.getPublications().subscribe({
      next: (publications) => {
        this.publications.set(publications);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Chargement des publications impossible', err);
        this.error.set('Les publications n’ont pas pu être chargées.');
        this.loading.set(false);
      },
    });
  }

  private setPublishing(publicationId: number, active: boolean): void {
    this.publishing.update((ids) => {
      const next = new Set(ids);

      if (active) {
        next.add(publicationId);
      } else {
        next.delete(publicationId);
      }

      return next;
    });
  }

  /**
   * Le back renvoie 502 avec le message du canal distant quand Facebook refuse
   * la publication : c'est l'information utile, on la préfère au message
   * générique quand elle est présente.
   */
  private deliveryErrorMessage(err: HttpErrorResponse): string {
    const reason = typeof err.error?.message === 'string' ? err.error.message : null;

    if (err.status === 501) {
      return 'Ce canal de diffusion n’est pas encore disponible.';
    }

    return reason
      ? `La publication n’a pas pu être diffusée sur Facebook : ${ reason }`
      : 'La publication n’a pas pu être diffusée sur Facebook.';
  }

  /** Relaie à l'utilisateur l'erreur des actions dont l'endpoint n'existe pas encore. */
  private runUnavailableAction(action: Observable<never>): void {
    action.subscribe({
      error: (err: Error) => {
        console.warn(err.message);
        this.notifications.warning('Cette action n’est pas encore disponible.');
      },
    });
  }
}
