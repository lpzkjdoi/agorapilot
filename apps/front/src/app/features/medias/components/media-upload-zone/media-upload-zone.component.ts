import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import {
  MAX_MEDIA_SIZE_BYTES,
  MEDIA_ACCEPT_ATTRIBUTE,
  RejectedFile,
  formatFileSize,
  rejectionReason,
} from '../../media.model';

/** Fichiers retenus et fichiers écartés d'un même dépôt. */
export interface FileSelection {
  accepted: File[];
  rejected: RejectedFile[];
}

@Component({
  selector: 'app-media-upload-zone',
  templateUrl: './media-upload-zone.component.html',
  styleUrl: './media-upload-zone.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediaUploadZoneComponent {
  /** Vrai pendant un envoi : la zone n'accepte plus de nouveau dépôt. */
  readonly uploading = input(false);

  /** Progression de l'envoi en cours, de 0 à 100, ou `null` hors envoi. */
  readonly progress = input<number | null>(null);

  readonly filesSelected = output<FileSelection>();

  protected readonly accept = MEDIA_ACCEPT_ATTRIBUTE;
  protected readonly maxSize = formatFileSize(MAX_MEDIA_SIZE_BYTES);

  /** Survol d'un glisser-déposer : pilote le retour visuel de la zone. */
  protected readonly dragging = signal(false);

  protected onDragOver(event: DragEvent): void {
    // Sans ce preventDefault, le navigateur ouvre le fichier dans l'onglet au
    // lieu de laisser la page traiter le dépôt.
    event.preventDefault();

    if (!this.uploading()) {
      this.dragging.set(true);
    }
  }

  protected onDragLeave(): void {
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);

    if (this.uploading()) {
      return;
    }

    this.emit(Array.from(event.dataTransfer?.files ?? []));
  }

  protected onInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.emit(Array.from(input.files ?? []));

    // Réinitialisé pour que redéposer le même fichier redéclenche l'événement.
    input.value = '';
  }

  /**
   * Trie les fichiers avant de les remonter. Le back refait ces contrôles sur le
   * contenu réel ; les faire ici évite seulement d'envoyer 20 Mo pour se voir
   * répondre 413.
   */
  private emit(files: File[]): void {
    if (files.length === 0) {
      return;
    }

    const accepted: File[] = [];
    const rejected: RejectedFile[] = [];

    for (const file of files) {
      const reason = rejectionReason(file);

      if (reason) {
        rejected.push({ filename: file.name, reason });
      } else {
        accepted.push(file);
      }
    }

    this.filesSelected.emit({ accepted, rejected });
  }
}
