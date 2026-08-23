import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  inject,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import { Media, isImageMedia, mediaLabel } from '../../media.model';
import { MediasService } from '../../medias.service';

@Component({
  selector: 'app-media-picker-modal',
  templateUrl: './media-picker-modal.component.html',
  styleUrl: './media-picker-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediaPickerModalComponent {
  private readonly mediasService = inject(MediasService);

  /** Médiathèque disponible, déjà chargée par la page. */
  readonly medias = input.required<readonly Media[]>();

  /** Sélection courante, dans l'ordre. */
  readonly selectedIds = input<readonly number[]>([]);

  readonly loading = input(false);
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly submitted = output<number[]>();

  /**
   * Sélection en cours d'édition. Un tableau et non un `Set` : l'ordre est
   * porteur de sens — le premier visuel sert de vignette et de première photo à
   * la diffusion — et un `Set` ne le garantirait pas à la lecture.
   *
   * `linkedSignal` et non un `signal` initialisé au constructeur : celui-ci
   * s'exécute avant que les entrées ne soient posées, la sélection de départ y
   * serait donc toujours vide.
   */
  private readonly draft = linkedSignal<readonly number[], number[]>({
    source: this.selectedIds,
    computation: (ids) => [...ids],
  });

  /** Les médias sélectionnés, dans l'ordre de la sélection. */
  protected readonly selection = computed(() => {
    const byId = new Map(this.medias().map((media) => [media.id, media]));
    return this.draft()
               .map((id) => byId.get(id))
               .filter((media): media is Media => media !== undefined);
  });

  /** Médiathèque proposée au choix : les archivés en sont exclus. */
  protected readonly available = computed(() => this.medias().filter((media) => !media.archived));

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  protected isSelected(media: Media): boolean {
    return this.draft().includes(media.id);
  }

  /** Rang affiché sur la vignette, à partir de 1. */
  protected rank(media: Media): number {
    return this.draft().indexOf(media.id) + 1;
  }

  protected toggle(media: Media): void {
    this.draft.update((ids) =>
      ids.includes(media.id) ? ids.filter((id) => id !== media.id) : [...ids, media.id],
    );
  }

  protected move(media: Media, offset: number): void {
    this.draft.update((ids) => {
      const from = ids.indexOf(media.id);
      const to = from + offset;

      if (from < 0 || to < 0 || to >= ids.length) {
        return ids;
      }

      const next = [...ids];
      next.splice(to, 0, ...next.splice(from, 1));
      return next;
    });
  }

  protected label(media: Media): string {
    return mediaLabel(media);
  }

  protected fileUrl(media: Media): string {
    return this.mediasService.fileUrl(media.id);
  }

  protected isImage(media: Media): boolean {
    return isImageMedia(media);
  }

  /** Les PDF sont stockables mais pas diffusables comme photo. */
  protected readonly nonPublishable = computed(() =>
    this.selection().filter((media) => !isImageMedia(media)).map((media) => mediaLabel(media)),
  );

  protected onSubmit(): void {
    if (this.saving()) {
      return;
    }

    this.submitted.emit([...this.draft()]);
  }
}
