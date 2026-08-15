import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  output,
} from '@angular/core';
import { CreatePublicationFormValue } from "../../publication.model";
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
  /** Vrai tant que la requête de création est en vol. */
  readonly creating = input(false);

  readonly closed = output<void>();
  readonly submitted = output<CreatePublicationFormValue>();

  /** La modale n'est rendue que lorsqu'elle est ouverte : la touche Échap la ferme. */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }
}
