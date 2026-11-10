import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from "../../../../core/auth/auth.service";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { NotificationService } from "../../../../core/notifications/notification.service";
import {
  Campaign,
  CampaignAction,
  CampaignFormValue,
  CampaignRequest,
} from "../../campaign.model";
import { CampaignsService } from "../../campaigns.service";
import {
  CampaignDetailComponent,
} from "../../components/campaign-detail/campaign-detail.component";
import {
  CampaignFormModalComponent,
} from "../../components/campaign-form-modal/campaign-form-modal.component";
import {
  CampaignListComponent,
} from "../../components/campaign-list/campaign-list.component";

/** Messages de chaque action du détail, en cas de succès puis d'échec. */
const ACTION_MESSAGES: Record<CampaignAction, { success: string, failure: string }> = {
  close: { success: 'Campagne clôturée.', failure: 'La campagne n’a pas pu être clôturée.' },
  cancel: { success: 'Campagne annulée.', failure: 'La campagne n’a pas pu être annulée.' },
  archive: { success: 'Campagne supprimée.', failure: 'La campagne n’a pas pu être supprimée.' },
};

@Component({
  selector: 'app-campaigns-page',
  imports: [
    CampaignListComponent,
    CampaignDetailComponent,
    CampaignFormModalComponent,
    LoaderComponent,
  ],
  templateUrl: './campaigns-page.component.html',
  styleUrl: './campaigns-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignsPageComponent {
  private readonly auth = inject(AuthService);
  protected readonly canWrite = computed(() => this.auth.can('campaigns:write'));
  private readonly campaignsService = inject(CampaignsService);
  private readonly notifications = inject(NotificationService);

  protected readonly campaigns = signal<Campaign[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** La modale ouverte : création, ou modification de la campagne sélectionnée. */
  protected readonly modal = signal<'create' | 'edit' | null>(null);
  protected readonly saving = signal(false);
  /** Une clôture, une annulation ou une suppression en vol. */
  protected readonly acting = signal(false);
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

  protected onSubmit(value: CampaignFormValue): void {
    const edited = this.modal() === 'edit' ? this.selected() : null;

    if (edited) {
      this.update(edited, value);
    } else {
      this.create(value);
    }
  }

  protected onAction(action: CampaignAction): void {
    const campaign = this.selected();

    if (!campaign || this.acting()) {
      return;
    }

    this.acting.set(true);
    const messages = ACTION_MESSAGES[action];

    const request: Observable<Campaign | void> = action === 'archive'
      ? this.campaignsService.archiveCampaign(campaign.id)
      : action === 'close'
        ? this.campaignsService.closeCampaign(campaign.id)
        : this.campaignsService.cancelCampaign(campaign.id);

    request.subscribe({
      next: (updated) => {
        if (updated) {
          this.replace(updated);
        } else {
          // Supprimée : elle quitte la liste, et la suivante prend sa place.
          this.campaigns.update((campaigns) => campaigns.filter((c) => c.id !== campaign.id));
          this.selectedId.set(this.campaigns()[0]?.id ?? null);
        }
        this.acting.set(false);
        this.notifications.success(messages.success);
      },
      error: (err) => {
        console.error(`Action « ${ action } » impossible sur la campagne ${ campaign.id }`, err);
        this.acting.set(false);
        this.notifications.error(messages.failure);
      },
    });
  }

  private create(value: CampaignFormValue): void {
    this.saving.set(true);

    this.campaignsService.createCampaign(CampaignsPageComponent.toRequest(value, null)).subscribe({
      next: (campaign) => {
        this.campaigns.update((campaigns) => [campaign, ...campaigns]);
        this.selectedId.set(campaign.id);
        this.saving.set(false);
        this.modal.set(null);
        this.notifications.success('Campagne créée.');
      },
      error: (err) => {
        console.error('Création de la campagne impossible', err);
        this.saving.set(false);
        this.notifications.error('La campagne n’a pas pu être créée.');
      },
    });
  }

  private update(campaign: Campaign, value: CampaignFormValue): void {
    this.saving.set(true);

    this.campaignsService.updateCampaign(campaign.id, CampaignsPageComponent.toRequest(value, campaign)).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.saving.set(false);
        this.modal.set(null);
        this.notifications.success('Campagne modifiée.');
      },
      error: (err) => {
        console.error('Modification de la campagne impossible', err);
        this.saving.set(false);
        this.notifications.error('La campagne n’a pas pu être modifiée.');
      },
    });
  }

  private replace(updated: Campaign): void {
    this.campaigns.update((campaigns) => campaigns.map((c) => c.id === updated.id ? updated : c));
  }

  private static toRequest(value: CampaignFormValue, original: Campaign | null): CampaignRequest {
    return {
      name: value.name,
      description: value.description,
      startDate: CampaignsPageComponent.toLocalDateTime(value.startDate, original?.startDate ?? null),
      endDate: CampaignsPageComponent.toLocalDateTime(value.endDate, original?.endDate ?? null),
    };
  }

  /**
   * `<input type="date">` rend `AAAA-MM-JJ` ; le back attend un
   * `LocalDateTime`. Un jour inchangé garde son heure d'origine — celle d'une
   * clôture, par exemple — plutôt que d'être ramené à minuit.
   */
  private static toLocalDateTime(date: string, original: string | null): string | null {
    if (!date) {
      return null;
    }

    return original?.slice(0, 10) === date ? original : `${ date }T00:00:00`;
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
