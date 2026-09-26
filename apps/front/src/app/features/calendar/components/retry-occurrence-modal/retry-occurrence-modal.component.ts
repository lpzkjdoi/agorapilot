import { DatePipe } from "@angular/common";
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
import { Occurrence } from "../../../occurrences/occurrence.model";
import { failureReason, toIsoDate } from "../../../occurrences/occurrence.utils";
import { RescheduleIntent } from "../edit-occurrence-modal/edit-occurrence-modal.component";

/**
 * Reprendre une diffusion en échec : choisir le jour, et éventuellement l'heure,
 * où ses canaux en échec repartiront.
 *
 * Aujourd'hui par défaut : on reprend en général une fois la cause réglée. Le
 * motif est rappelé, avec une mise en garde : certains échecs (coupure réseau,
 * diffusion interrompue) surviennent après la mise en ligne, et reprendre
 * publierait alors deux fois.
 */
@Component({
  selector: 'app-retry-occurrence-modal',
  imports: [ReactiveFormsModule, DatePipe, LoaderComponent],
  templateUrl: './retry-occurrence-modal.component.html',
  styleUrl: './retry-occurrence-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RetryOccurrenceModalComponent {
  readonly occurrence = input.required<Occurrence>();

  /** Vrai tant que la reprise est en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly retried = output<RescheduleIntent>();

  protected readonly minDate = toIsoDate(new Date());
  protected readonly reason = failureReason;

  readonly form = new FormGroup({
    date: new FormControl(this.minDate, { nonNullable: true, validators: [Validators.required] }),
    time: new FormControl('', { nonNullable: true }),
  });

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onSubmit(): void {
    if (this.saving()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { date, time } = this.form.getRawValue();
    this.retried.emit({ occurrence: this.occurrence(), date, time: time || null });
  }
}
