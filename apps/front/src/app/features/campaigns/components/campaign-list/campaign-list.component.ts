import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { CAMPAIGN_STATUS_LABELS, Campaign } from "../../campaign.model";

@Component({
  selector: 'app-campaign-list',
  imports: [DatePipe],
  templateUrl: './campaign-list.component.html',
  styleUrl: './campaign-list.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignListComponent {
  readonly campaigns = input.required<Campaign[]>();
  readonly selectedId = input<number | null>(null);

  readonly selected = output<Campaign>();
  readonly createRequested = output<void>();

  protected readonly statusLabels = CAMPAIGN_STATUS_LABELS;
  protected readonly search = signal('');

  protected readonly filtered = computed(() => {
    const search = this.search().trim().toLowerCase();

    if (!search) {
      return this.campaigns();
    }

    return this.campaigns().filter((campaign) => campaign.name.toLowerCase().includes(search));
  });

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
}
