import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Publication } from "../../publication.model";

@Component({
  selector: 'app-publication-card',
  imports: [DatePipe, LoaderComponent],
  templateUrl: './publication-card.component.html',
  styleUrl: './publication-card.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationCardComponent {
  readonly publication = input.required<Publication>();

  /** Diffusion Facebook en cours : le bouton passe en attente et se verrouille. */
  readonly publishing = input(false);

  readonly generateXlsx = output<Publication>();
  readonly publishOnFacebook = output<Publication>();
  readonly assignCampaign = output<Publication>();

  readonly verified = computed(() => this.publication().status === 'VERIFIED');
  readonly campaignName = computed(() => this.publication().campaign?.name ?? null);
}
