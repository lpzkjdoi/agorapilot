import { ChangeDetectionStrategy, Component, HostListener, effect, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Media, UpdateMediaRequest, mediaLabel } from '../../media.model';

@Component({
  selector: 'app-media-edit-modal',
  imports: [ReactiveFormsModule],
  templateUrl: './media-edit-modal.component.html',
  styleUrl: './media-edit-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediaEditModalComponent {
  readonly media = input.required<Media>();

  /** Vrai tant que la requête de modification est en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly submitted = output<UpdateMediaRequest>();

  readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
    altText: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
  });

  constructor() {
    // La modale est rendue avec son média : le formulaire s'aligne dessus dès
    // qu'il est disponible, et à chaque changement de média.
    effect(() => {
      const media = this.media();
      this.form.setValue({
        title: media.title ?? '',
        altText: media.altText ?? '',
      });
    });
  }

  protected label(): string {
    return mediaLabel(this.media());
  }

  /** La modale n'est rendue que lorsqu'elle est ouverte : la touche Échap la ferme. */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  protected onSubmit(): void {
    if (this.saving()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { title, altText } = this.form.getRawValue();

    // Les champs vidés sont envoyés en chaîne vide, que le back traduit en
    // valeur absente : c'est ce qui permet d'effacer un titre.
    this.submitted.emit({ title: title.trim(), altText: altText.trim() });
  }
}
