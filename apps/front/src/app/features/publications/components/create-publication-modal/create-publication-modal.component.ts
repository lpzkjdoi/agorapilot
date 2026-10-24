import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  input,
  output,
} from '@angular/core';
import { CreatePublicationFormValue, Publication } from "../../publication.model";
import {
  PublicationFormComponent,
} from "../publication-form/publication-form.component";

@Component({
  selector: 'app-create-publication-modal',
  imports: [PublicationFormComponent],
  templateUrl: './create-publication-modal.component.html',
  styleUrl: './create-publication-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreatePublicationModalComponent {
  /** Vrai tant que la requête de création (ou d'enregistrement) est en vol. */
  readonly creating = input(false);

  /** Publication à modifier ; absente, la modale crée une publication. */
  readonly publication = input<Publication | null>(null);

  protected readonly initialValue = computed<CreatePublicationFormValue | null>(() => {
    const publication = this.publication();

    return publication ? { content: publication.content, status: publication.status === 'VERIFIED' } : null;
  });

  readonly closed = output<void>();
  readonly submitted = output<CreatePublicationFormValue>();

  /** La modale n'est rendue que lorsqu'elle est ouverte : la touche Échap la ferme. */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }
}
