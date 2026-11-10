import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  output,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { CreateCampaignFormValue } from "../../campaign.model";

@Component({
  selector: 'app-create-campaign-modal',
  imports: [ReactiveFormsModule, LoaderComponent],
  templateUrl: './create-campaign-modal.component.html',
  styleUrl: './create-campaign-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateCampaignModalComponent {
  /** Vrai tant que la requête de création est en vol. */
  readonly creating = input(false);

  readonly closed = output<void>();
  readonly submitted = output<CreateCampaignFormValue>();

  /**
   * Le back contrôle les dates à la journée : une campagne peut démarrer
   * aujourd'hui, pas avant.
   */
  protected readonly minDate = CreateCampaignModalComponent.today();

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255)],
    }),
    description: new FormControl('', { nonNullable: true }),
    startDate: new FormControl('', { nonNullable: true }),
    endDate: new FormControl('', { nonNullable: true }),
  });

  /**
   * Aujourd'hui dans le fuseau du navigateur : `toISOString()` bascule en UTC
   * et renverrait la veille entre minuit et l'aube, heure française.
   */
  private static today(): string {
    const date = new Date();

    const month = `${ date.getMonth() + 1 }`.padStart(2, '0');
    const day = `${ date.getDate() }`.padStart(2, '0');

    return `${ date.getFullYear() }-${ month }-${ day }`;
  }

  /** La modale n'est rendue que lorsqu'elle est ouverte : la touche Échap la ferme. */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onSubmit(): void {
    // Un double envoi créerait deux campagnes : on ignore le second.
    if (this.creating()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitted.emit(this.form.getRawValue());
  }
}
