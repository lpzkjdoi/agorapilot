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
import { Publication } from "../../../publications/publication.model";
import { ScheduleFormValue } from "../../occurrence.model";
import { toIsoDate } from "../../occurrence.utils";

/**
 * Programme la diffusion d'une publication pour un jour.
 *
 * L'heure est facultative : laissée vide, le back choisit le créneau dans la
 * fenêtre de publication du jour, en répartissant les publications ; saisie,
 * elle est fixée et ne bougera plus.
 */
@Component({
  selector: 'app-schedule-occurrence-modal',
  imports: [ReactiveFormsModule, LoaderComponent],
  templateUrl: './schedule-occurrence-modal.component.html',
  styleUrl: './schedule-occurrence-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleOccurrenceModalComponent {
  readonly publication = input.required<Publication>();

  /** Vrai tant que la requête de programmation est en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly submitted = output<ScheduleFormValue>();

  /**
   * Aujourd'hui reste proposé : le back l'accepte tant que la fenêtre du jour
   * n'est pas close, et explique son refus sinon.
   */
  protected readonly minDate = toIsoDate(new Date());

  readonly form = new FormGroup({
    date: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    time: new FormControl('', { nonNullable: true }),
    facebook: new FormControl(true, { nonNullable: true, validators: [Validators.requiredTrue] }),
  });

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onSubmit(): void {
    // Un double envoi programmerait deux fois la même publication.
    if (this.saving()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { date, time } = this.form.getRawValue();
    this.submitted.emit({ date, time, channels: ['FACEBOOK'] });
  }
}
