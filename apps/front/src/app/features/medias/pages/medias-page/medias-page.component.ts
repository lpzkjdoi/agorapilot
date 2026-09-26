import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { concatMap, from } from 'rxjs';
import { LoaderComponent } from '../../../../core/loader/loader.component';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { MediaCardComponent } from '../../components/media-card/media-card.component';
import { MediaEditModalComponent } from '../../components/media-edit-modal/media-edit-modal.component';
import {
  FileSelection,
  MediaUploadZoneComponent,
} from '../../components/media-upload-zone/media-upload-zone.component';
import { Media, UpdateMediaRequest, mediaLabel } from '../../media.model';
import { MediasService } from '../../medias.service';

type ArchivedFilter = 'CURRENT' | 'ARCHIVED';

@Component({
  selector: 'app-medias-page',
  imports: [MediaCardComponent, MediaEditModalComponent, MediaUploadZoneComponent, LoaderComponent],
  templateUrl: './medias-page.component.html',
  styleUrl: './medias-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediasPageComponent {
  private readonly mediasService = inject(MediasService);
  private readonly notifications = inject(NotificationService);

  protected readonly medias = signal<Media[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly uploading = signal(false);
  protected readonly uploadProgress = signal<number | null>(null);

  /** Média en cours d'édition, `null` quand la modale est fermée. */
  protected readonly editing = signal<Media | null>(null);
  protected readonly saving = signal(false);

  /** Ids des médias dont une suppression ou un archivage est en vol. */
  protected readonly busy = signal<ReadonlySet<number>>(new Set());

  protected readonly filter = signal<ArchivedFilter>('CURRENT');

  protected readonly visibleMedias = computed(() => {
    const archived = this.filter() === 'ARCHIVED';
    return this.medias().filter((media) => media.archived === archived);
  });

  protected readonly archivedCount = computed(
    () => this.medias().filter((media) => media.archived).length,
  );

  constructor() {
    this.loadMedias();
  }

  // ------------------------------- Dépôt ---------------------------------

  protected onFilesSelected(selection: FileSelection): void {
    for (const rejected of selection.rejected) {
      this.notifications.warning(`${ rejected.filename } — ${ rejected.reason }.`);
    }

    if (selection.accepted.length === 0) {
      return;
    }

    this.uploading.set(true);
    this.uploadProgress.set(0);

    let uploaded = 0;

    // `concatMap` et non `mergeMap` : les fichiers partent l'un après l'autre.
    // Le VPS est en 1 vCPU, et une barre de progression n'a de sens que pour un
    // envoi à la fois.
    from(selection.accepted)
      .pipe(concatMap((file) => this.mediasService.upload(file)))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.uploadProgress.set(Math.round((event.loaded / event.total) * 100));
            return;
          }

          if (event.type === HttpEventType.Response && event.body) {
            uploaded += 1;
            const media = event.body;
            this.medias.update((medias) => [media, ...medias]);
            this.uploadProgress.set(0);
          }
        },
        error: (err: HttpErrorResponse) => {
          console.error('Dépôt du média impossible', err);
          this.finishUpload();
          this.notifications.error(this.uploadErrorMessage(err));
        },
        complete: () => {
          this.finishUpload();
          this.notifications.success(
            uploaded > 1 ? `${ uploaded } médias ajoutés.` : 'Média ajouté.',
          );
        },
      });
  }

  // ------------------------------ Édition --------------------------------

  protected onEdit(media: Media): void {
    this.editing.set(media);
  }

  protected onEditSubmitted(request: UpdateMediaRequest): void {
    const media = this.editing();

    if (!media) {
      return;
    }

    this.saving.set(true);

    this.mediasService.updateMedia(media.id, request).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.saving.set(false);
        this.editing.set(null);
        this.notifications.success('Média mis à jour.');
      },
      error: (err) => {
        console.error('Mise à jour du média impossible', err);
        this.saving.set(false);
        this.notifications.error('Le média n’a pas pu être mis à jour.');
      },
    });
  }

  protected onToggleArchive(media: Media): void {
    this.setBusy(media.id, true);

    this.mediasService.updateMedia(media.id, { archived: !media.archived }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.setBusy(media.id, false);
        this.notifications.success(updated.archived ? 'Média archivé.' : 'Média désarchivé.');
      },
      error: (err) => {
        console.error('Archivage du média impossible', err);
        this.setBusy(media.id, false);
        this.notifications.error('L’archivage n’a pas pu être enregistré.');
      },
    });
  }

  // ---------------------------- Suppression ------------------------------

  protected onDelete(media: Media): void {
    this.setBusy(media.id, true);

    this.mediasService.deleteMedia(media.id).subscribe({
      next: () => {
        this.medias.update((medias) => medias.filter((item) => item.id !== media.id));
        this.setBusy(media.id, false);
        this.notifications.success(`${ mediaLabel(media) } supprimé.`);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Suppression du média impossible', err);
        this.setBusy(media.id, false);
        this.notifications.error(this.deleteErrorMessage(err));
      },
    });
  }

  // ------------------------------ Interne --------------------------------

  private loadMedias(): void {
    this.loading.set(true);

    this.mediasService.getMedias().subscribe({
      next: (medias) => {
        this.medias.set(medias);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Chargement des médias impossible', err);
        this.error.set('Les médias n’ont pas pu être chargés.');
        this.loading.set(false);
      },
    });
  }

  private finishUpload(): void {
    this.uploading.set(false);
    this.uploadProgress.set(null);
  }

  private replace(updated: Media): void {
    this.medias.update((medias) =>
      medias.map((media) => (media.id === updated.id ? updated : media)),
    );
  }

  private setBusy(mediaId: number, active: boolean): void {
    this.busy.update((ids) => {
      const next = new Set(ids);

      if (active) {
        next.add(mediaId);
      } else {
        next.delete(mediaId);
      }

      return next;
    });
  }

  /**
   * Le back distingue le format refusé (415) de la taille (413) : ce sont deux
   * corrections différentes pour l'utilisateur, autant le lui dire.
   */
  private uploadErrorMessage(err: HttpErrorResponse): string {
    const reason = typeof err.error?.message === 'string' ? err.error.message : null;

    if (err.status === 413) {
      return 'Le fichier dépasse la taille maximale autorisée.';
    }

    if (err.status === 415) {
      return reason ?? 'Ce format de fichier n’est pas accepté.';
    }

    return 'Le média n’a pas pu être déposé.';
  }

  /**
   * 409 signifie que le média est rattaché à des publications. Le back ne renvoie
   * ce code qu'une fois la liaison en place ; le message est prêt pour ce jour-là.
   */
  private deleteErrorMessage(err: HttpErrorResponse): string {
    if (err.status === 409) {
      return 'Ce média est utilisé par des publications : détachez-le avant de le supprimer.';
    }

    return 'Le média n’a pas pu être supprimé.';
  }
}
