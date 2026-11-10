import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import {
  CAMPAIGN_STATUS_LABELS,
  Campaign,
  CampaignAction,
  campaignStats,
  canCancel,
  canClose,
} from "../../campaign.model";

/** Ce que la confirmation annonce, pour chaque action. */
const CONFIRMATIONS: Record<CampaignAction, { question: string, button: string }> = {
  close: {
    question: 'Clôturer la campagne maintenant ? Sa date de fin passera à aujourd’hui.',
    button: 'Confirmer la clôture',
  },
  cancel: {
    question: 'Annuler la campagne ? Les diffusions déjà programmées de ses publications partiront quand même.',
    button: 'Confirmer l’annulation',
  },
  archive: {
    question: 'Supprimer la campagne ? Ses publications sont conservées et redeviennent sans campagne.',
    button: 'Confirmer la suppression',
  },
};

@Component({
  selector: 'app-campaign-detail',
  imports: [DatePipe],
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

  readonly edit = output<void>();
  readonly actionConfirmed = output<CampaignAction>();

  protected readonly statusLabels = CAMPAIGN_STATUS_LABELS;
  protected readonly confirmations = CONFIRMATIONS;
  protected readonly stats = computed(() => campaignStats(this.campaign()));
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
