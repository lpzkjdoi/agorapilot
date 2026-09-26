import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  CAMPAIGN_STATUS_LABELS,
  Campaign,
  campaignStats,
} from "../../campaign.model";

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

  protected readonly statusLabels = CAMPAIGN_STATUS_LABELS;
  protected readonly stats = computed(() => campaignStats(this.campaign()));
}
