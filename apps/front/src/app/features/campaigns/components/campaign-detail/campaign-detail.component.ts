import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { Occurrence } from "../../../occurrences/occurrence.model";
import { failureReason } from "../../../occurrences/occurrence.utils";
import { DELIVERY_CHANNEL_LABELS, Publication } from "../../../publications/publication.model";
import {
  CAMPAIGN_STATUS_LABELS,
  Campaign,
  CampaignAction,
  canCancel,
  canClose,
} from "../../campaign.model";
import { planningByPublication, planningStats } from "../../campaign-planning";
import { CampaignCalendarComponent } from "../campaign-calendar/campaign-calendar.component";

/** Ce que la confirmation annonce, pour chaque action. */
const CONFIRMATIONS: Record<CampaignAction, { question: string, button: string }> = {
  close: {
    question: 'Clôturer la campagne maintenant ? Sa date de fin passera à aujourd’hui.',
    button: 'Confirmer la clôture',
  },
  cancel: {
    question: 'Annuler la campagne ? Les diffusions programmées de ses publications seront déprogrammées.',
    button: 'Confirmer l’annulation',
  },
  archive: {
    question: 'Supprimer la campagne ? Ses publications sont conservées et redeviennent sans campagne.',
    button: 'Confirmer la suppression',
  },
};

@Component({
  selector: 'app-campaign-detail',
  imports: [DatePipe, CampaignCalendarComponent],
  templateUrl: './campaign-detail.component.html',
  styleUrl: './campaign-detail.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignDetailComponent {
  readonly campaign = input.required<Campaign>();
  /** Droit `campaigns:write` : sans lui, le détail reste en lecture seule. */
  readonly canWrite = input(false);
  /** Vrai tant qu'une action est en vol : les boutons sont désactivés. */
  readonly busy = input(false);
  /** Diffusions des publications de la campagne ; `null` tant qu'elles se chargent. */
  readonly occurrences = input<Occurrence[] | null>(null);
  /** Le chargement des diffusions a échoué : les compteurs ne sont pas affichés. */
  readonly planningUnavailable = input(false);
  /** Droit `occurrences:schedule` : programmer une publication depuis la campagne. */
  readonly canSchedule = input(false);
  /** Droit `publications:write` : ajouter ou retirer des publications. */
  readonly canAssign = input(false);

  readonly edit = output<void>();
  readonly actionConfirmed = output<CampaignAction>();
  readonly schedule = output<Publication>();
  readonly addPublications = output<void>();
  readonly detach = output<Publication>();

  protected readonly statusLabels = CAMPAIGN_STATUS_LABELS;
  protected readonly confirmations = CONFIRMATIONS;
  /** Onglet affiché ; il est conservé d'une campagne à l'autre. */
  protected readonly tab = signal<'publications' | 'calendar'>('publications');

  protected readonly channelLabels = DELIVERY_CHANNEL_LABELS;
  protected readonly failureReason = failureReason;
  protected readonly stats = computed(() => planningStats(this.campaign().publications, this.occurrences() ?? []));
  protected readonly plannings = computed(() => planningByPublication(this.occurrences() ?? []));
  protected readonly closable = computed(() => canClose(this.campaign()));
  protected readonly cancelable = computed(() => canCancel(this.campaign()));

  /**
   * L'action en attente de confirmation, sur la carte plutôt que par un
   * `confirm()` natif — comme la suppression d'une publication. Changer de
   * campagne l'abandonne.
   */
  protected readonly pending = linkedSignal<number, CampaignAction | null>({
    source: () => this.campaign().id,
    computation: () => null,
  });

  protected confirm(): void {
    const action = this.pending();

    if (action) {
      this.pending.set(null);
      this.actionConfirmed.emit(action);
    }
  }
}
