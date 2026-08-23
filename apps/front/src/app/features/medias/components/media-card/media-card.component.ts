import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { formatFileSize, isImageMedia, Media, mediaLabel } from '../../media.model';
import { MediasService } from '../../medias.service';

@Component({
  selector: 'app-media-card',
  imports: [DatePipe],
  templateUrl: './media-card.component.html',
  styleUrl: './media-card.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediaCardComponent {
  private readonly mediasService = inject(MediasService);

  readonly media = input.required<Media>();

  /** Vrai pendant la suppression : désactive les actions et l'annonce à l'écran. */
  readonly busy = input(false);

  readonly renamed = output<Media>();
  readonly archiveToggled = output<Media>();
  readonly deleted = output<Media>();

  protected readonly label = computed(() => mediaLabel(this.media()));
  protected readonly isImage = computed(() => isImageMedia(this.media()));
  protected readonly fileUrl = computed(() => this.mediasService.fileUrl(this.media().id));
  protected readonly size = computed(() => formatFileSize(this.media().sizeBytes));

  protected readonly dimensions = computed(() => {
    const { width, height } = this.media();
    return width && height ? `${ width } × ${ height }` : null;
  });

  /**
   * Le texte alternatif décrit l'affiche quand il a été saisi. À défaut, la
   * vignette n'apporte rien qu'un lecteur d'écran puisse annoncer utilement :
   * un alt vide la lui fait ignorer, le titre étant déjà lu juste en dessous.
   */
  protected readonly alt = computed(() => this.media().altText?.trim() ?? '');

  /**
   * Suppression en deux temps plutôt qu'un `confirm()` natif : le geste reste
   * dans la page, se teste sans piéger `window`, et s'annule d'un clic.
   */
  protected readonly confirmingDelete = signal(false);

  protected confirmDelete(): void {
    this.confirmingDelete.set(false);
    this.deleted.emit(this.media());
  }
}
