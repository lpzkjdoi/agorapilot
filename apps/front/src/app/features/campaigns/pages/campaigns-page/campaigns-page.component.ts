import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, forkJoin, map, Observable, of, startWith, switchMap } from 'rxjs';
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
  ScheduleOccurrenceModalComponent,
} from "../../../occurrences/components/schedule-occurrence-modal/schedule-occurrence-modal.component";
import { Occurrence, ScheduleFormValue } from "../../../occurrences/occurrence.model";
import {
  apiErrorMessage,
  scheduledMessage,
  toCreateOccurrencesRequest,
} from "../../../occurrences/occurrence.utils";
import { OccurrencesService } from "../../../occurrences/occurrences.service";
import { Publication } from "../../../publications/publication.model";
import { PublicationsService } from "../../../publications/publications.service";
import {
  AddPublicationsModalComponent,
} from "../../components/add-publications-modal/add-publications-modal.component";
import {
  CampaignDetailComponent,
} from "../../components/campaign-detail/campaign-detail.component";
import {
  CampaignFormModalComponent,
} from "../../components/campaign-form-modal/campaign-form-modal.component";
import {
  CampaignListComponent,
} from "../../components/campaign-list/campaign-list.component";

/** Diffusions de la campagne affichée : `null` pendant le chargement. */
interface PlanningState {
  occurrences: Occurrence[] | null
  failed: boolean
}

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
    AddPublicationsModalComponent,
    ScheduleOccurrenceModalComponent,
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
  protected readonly canSchedule = computed(() => this.auth.can('occurrences:schedule'));
  /** Rattacher ou détacher une publication est une écriture sur la publication. */
  protected readonly canAssign = computed(() => this.auth.can('publications:write'));
  private readonly campaignsService = inject(CampaignsService);
  private readonly publicationsService = inject(PublicationsService);
  private readonly occurrencesService = inject(OccurrencesService);
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

  /** Incrémenté pour recharger les diffusions après une programmation ou un rattachement. */
  private readonly planningVersion = signal(0);

  /**
   * Diffusions de la campagne sélectionnée, rechargées à chaque changement de
   * sélection ; `switchMap` abandonne la réponse d'une campagne quittée entre-temps.
   */
  protected readonly planning = toSignal(
    toObservable(computed(() => ({ id: this.selectedId(), version: this.planningVersion() }))).pipe(
      switchMap(({ id }) => id === null
        ? of<PlanningState>({ occurrences: [], failed: false })
        : this.campaignsService.getOccurrences(id).pipe(
          map((occurrences): PlanningState => ({ occurrences, failed: false })),
          startWith<PlanningState>({ occurrences: null, failed: false }),
          catchError((err) => {
            console.error(`Diffusions de la campagne ${ id } indisponibles`, err);
            return of<PlanningState>({ occurrences: null, failed: true });
          }),
        )),
    ),
    { initialValue: { occurrences: null, failed: false } satisfies PlanningState },
  );

  /** Publications proposées à l'ajout ; `null` tant qu'elles se chargent. */
  protected readonly candidates = signal<Publication[] | null>(null);
  protected readonly addModalOpen = signal(false);
  protected readonly adding = signal(false);
  protected readonly detaching = signal(false);
  protected readonly schedulingFor = signal<Publication | null>(null);
  protected readonly scheduling = signal(false);

  constructor() {
    this.loadCampaigns();
  }

  // ----------------------------- Publications de la campagne -----------------------------

  protected onAddRequested(): void {
    this.candidates.set(null);
    this.addModalOpen.set(true);

    this.publicationsService.getPublications().subscribe({
      next: (publications) => this.candidates.set(publications),
      error: (err) => {
        console.error('Chargement des publications impossible', err);
        this.addModalOpen.set(false);
        this.notifications.error('Les publications n’ont pas pu être chargées.');
      },
    });
  }

  protected onAddSubmitted(publicationIds: number[]): void {
    const campaign = this.selected();

    if (!campaign) {
      return;
    }

    this.adding.set(true);

    forkJoin(publicationIds.map((id) => this.publicationsService.assignToCampaign(id, campaign.id))).subscribe({
      next: (added) => {
        this.attach(campaign.id, added);
        this.adding.set(false);
        this.addModalOpen.set(false);
        this.planningVersion.update((version) => version + 1);
        this.notifications.success(added.length === 1
          ? 'Publication ajoutée à la campagne.'
          : `${ added.length } publications ajoutées à la campagne.`);
      },
      error: (err) => {
        console.error('Ajout des publications impossible', err);
        this.adding.set(false);
        this.addModalOpen.set(false);
        this.notifications.error('Les publications n’ont pas toutes pu être ajoutées.');
        // Certaines ont pu l'être : la liste est relue plutôt que devinée.
        this.loadCampaigns(campaign.id);
      },
    });
  }

  protected onDetach(publication: Publication): void {
    const campaign = this.selected();

    if (!campaign || this.detaching()) {
      return;
    }

    this.detaching.set(true);

    this.publicationsService.assignToCampaign(publication.id, null).subscribe({
      next: () => {
        this.campaigns.update((campaigns) => campaigns.map((c) => c.id !== campaign.id ? c : {
          ...c,
          publications: c.publications.filter((p) => p.id !== publication.id),
        }));
        this.detaching.set(false);
        this.planningVersion.update((version) => version + 1);
        this.notifications.success('Publication retirée de la campagne.');
      },
      error: (err) => {
        console.error('Retrait de la publication impossible', err);
        this.detaching.set(false);
        this.notifications.error('La publication n’a pas pu être retirée.');
      },
    });
  }

  protected onScheduleSubmitted(value: ScheduleFormValue): void {
    const publication = this.schedulingFor();

    if (!publication) {
      return;
    }

    this.scheduling.set(true);

    this.occurrencesService.createAll(toCreateOccurrencesRequest(publication.id, value)).subscribe({
      next: (occurrences) => {
        this.scheduling.set(false);
        this.schedulingFor.set(null);
        this.planningVersion.update((version) => version + 1);
        this.notifications.success(scheduledMessage(occurrences));
      },
      error: (err: HttpErrorResponse) => {
        console.error('Programmation impossible', err);
        this.scheduling.set(false);
        // La modale reste ouverte : le motif invite à corriger la saisie.
        this.notifications.error(apiErrorMessage(err, value.slots.length > 1
          ? 'Aucune diffusion n’a été programmée.'
          : 'La diffusion n’a pas pu être programmée.'));
      },
    });
  }

  /**
   * Range les publications rattachées dans leur nouvelle campagne et les retire
   * de celle qu'elles quittent, sans relire toute la liste.
   */
  private attach(campaignId: number, added: Publication[]): void {
    const ids = new Set(added.map((publication) => publication.id));

    this.campaigns.update((campaigns) => campaigns.map((c) => {
      const kept = c.publications.filter((publication) => !ids.has(publication.id));
      return { ...c, publications: c.id === campaignId ? [...kept, ...added] : kept };
    }));
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

  /** Charge la liste ; `keepId` reste sélectionnée si elle y figure encore. */
  private loadCampaigns(keepId: number | null = null): void {
    this.loading.set(true);

    this.campaignsService.getCampaigns().subscribe({
      next: (campaigns) => {
        this.campaigns.set(campaigns);
        const kept = campaigns.some((campaign) => campaign.id === keepId);
        this.selectedId.set(kept ? keepId : campaigns[0]?.id ?? null);
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
