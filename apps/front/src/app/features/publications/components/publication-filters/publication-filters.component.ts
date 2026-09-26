import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Campaign } from "../../../campaigns/campaign.model";
import {
  PublicationCampaignFilter,
  PublicationStatusFilter,
} from "../../publication.model";

/** Compteurs affichés dans le bandeau sous les filtres. */
export interface PublicationStats {
  total: number
  verified: number
  drafts: number
  inCampaigns: number
}

@Component({
  selector: 'app-publication-filters',
  imports: [],
  templateUrl: './publication-filters.component.html',
  styleUrl: './publication-filters.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationFiltersComponent {
  readonly campaigns = input.required<Campaign[]>();
  readonly stats = input.required<PublicationStats>();

  readonly searchChanged = output<string>();
  readonly statusChanged = output<PublicationStatusFilter>();
  readonly campaignChanged = output<PublicationCampaignFilter>();

  protected onSearch(event: Event): void {
    this.searchChanged.emit((event.target as HTMLInputElement).value);
  }

  protected onStatus(event: Event): void {
    this.statusChanged.emit((event.target as HTMLSelectElement).value as PublicationStatusFilter);
  }

  protected onCampaign(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.campaignChanged.emit(value === 'ALL' || value === 'NONE' ? value : Number(value));
  }
}
