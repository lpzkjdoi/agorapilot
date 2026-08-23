import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { MediasService } from "../../../medias/medias.service";
import { isPublishableMedia, Publication } from "../../publication.model";

@Component({
  selector: 'app-publication-card',
  imports: [DatePipe, LoaderComponent],
  templateUrl: './publication-card.component.html',
  styleUrl: './publication-card.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationCardComponent {
  private readonly mediasService = inject(MediasService);

  readonly publication = input.required<Publication>();

  /** Diffusion Facebook en cours : le bouton passe en attente et se verrouille. */
  readonly publishing = input(false);

  readonly generateXlsx = output<Publication>();
  readonly manageMedias = output<Publication>();
  readonly publishOnFacebook = output<Publication>();
  readonly assignCampaign = output<Publication>();

  readonly verified = computed(() => this.publication().status === 'VERIFIED');
  readonly campaignName = computed(() => this.publication().campaign?.name ?? null);

  readonly mediaCount = computed(() => this.publication().medias?.length ?? 0);

  /**
   * Vignette : la première **image** rattachée. Un PDF placé en tête ne peut pas
   * en servir, la carte retombe alors sur l'icône « sans média ».
   */
  private readonly thumbnail = computed(
    () => this.publication().medias?.find(isPublishableMedia) ?? null,
  );

  readonly thumbnailUrl = computed(() => {
    const media = this.thumbnail();
    return media ? this.mediasService.fileUrl(media.id) : null;
  });

  /**
   * Vide en l'absence de description : la vignette n'apporte alors rien qu'un
   * lecteur d'écran puisse annoncer, le contenu de la publication étant lu juste
   * en dessous.
   */
  readonly thumbnailAlt = computed(() => this.thumbnail()?.altText?.trim() ?? '');
}
