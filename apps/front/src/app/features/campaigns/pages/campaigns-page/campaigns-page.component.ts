import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { NotificationService } from "../../../../core/notifications/notification.service";
import { Campaign, CreateCampaignFormValue } from "../../campaign.model";
import { CampaignsService } from "../../campaigns.service";
import {
  CampaignDetailComponent,
} from "../../components/campaign-detail/campaign-detail.component";
import {
  CampaignListComponent,
} from "../../components/campaign-list/campaign-list.component";
import {
  CreateCampaignModalComponent,
} from "../../components/create-campaign-modal/create-campaign-modal.component";

@Component({
  selector: 'app-campaigns-page',
  imports: [
    CampaignListComponent,
    CampaignDetailComponent,
    CreateCampaignModalComponent,
    LoaderComponent,
  ],
  templateUrl: './campaigns-page.component.html',
  styleUrl: './campaigns-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignsPageComponent {
  private readonly campaignsService = inject(CampaignsService);
  private readonly notifications = inject(NotificationService);

  protected readonly campaigns = signal<Campaign[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly createModalOpen = signal(false);
  protected readonly creating = signal(false);
  protected readonly selectedId = signal<number | null>(null);

  /**
   * La campagne sélectionnée est retrouvée dans la liste plutôt que stockée :
   * une campagne créée ou rechargée reste ainsi celle qui est affichée.
   */
  protected readonly selected = computed(() => {
    const id = this.selectedId();
    return this.campaigns().find((campaign) => campaign.id === id) ?? null;
  });

  constructor() {
    this.loadCampaigns();
  }

  protected onCreate(value: CreateCampaignFormValue): void {
    this.creating.set(true);

    this.campaignsService
        .createCampaign({
          name: value.name,
          description: value.description,
          startDate: CampaignsPageComponent.toLocalDateTime(value.startDate),
          endDate: CampaignsPageComponent.toLocalDateTime(value.endDate),
        })
        .subscribe({
          next: (campaign) => {
            this.campaigns.update((campaigns) => [campaign, ...campaigns]);
            this.selectedId.set(campaign.id);
            this.creating.set(false);
            this.createModalOpen.set(false);
            this.notifications.success('Campagne créée.');
          },
          error: (err) => {
            console.error('Création de la campagne impossible', err);
            this.creating.set(false);
            this.notifications.error('La campagne n’a pas pu être créée.');
          },
        });
  }

  /** `<input type="date">` rend `AAAA-MM-JJ` ; le back attend un `LocalDateTime`. */
  private static toLocalDateTime(date: string): string | null {
    return date ? `${ date }T00:00:00` : null;
  }

  private loadCampaigns(): void {
    this.loading.set(true);

    this.campaignsService.getCampaigns().subscribe({
      next: (campaigns) => {
        this.campaigns.set(campaigns);
        this.selectedId.set(campaigns[0]?.id ?? null);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Chargement des campagnes impossible', err);
        this.error.set('Les campagnes n’ont pas pu être chargées.');
        this.loading.set(false);
      },
    });
  }
}
