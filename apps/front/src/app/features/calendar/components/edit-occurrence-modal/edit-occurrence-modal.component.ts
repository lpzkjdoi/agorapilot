import { DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnInit,
  input,
  output,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Occurrence } from "../../../occurrences/occurrence.model";
import { scheduledDay, scheduledTime, toIsoDate } from "../../../occurrences/occurrence.utils";

/** Demande de déplacement : `time` à `null` rend l'heure automatique. */
export interface RescheduleIntent {
  occurrence: Occurrence
  date: string
  time: string | null
}

/**
 * Modifier une diffusion programmée : changer de jour — l'équivalent clavier du
 * glisser-déposer —, fixer l'heure, la rendre automatique, ou annuler.
 */
@Component({
  selector: 'app-edit-occurrence-modal',
  imports: [ReactiveFormsModule, DatePipe, LoaderComponent],
  templateUrl: './edit-occurrence-modal.component.html',
  styleUrl: './edit-occurrence-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditOccurrenceModalComponent implements OnInit {
  readonly occurrence = input.required<Occurrence>();

  /** Vrai tant qu'une modification ou une annulation est en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly rescheduled = output<RescheduleIntent>();
  readonly cancelled = output<Occurrence>();

  protected readonly minDate = toIsoDate(new Date());
  protected readonly time = scheduledTime;

  /** L'annulation se fait en deux temps. */
  protected readonly confirmingCancel = signal(false);

  readonly form = new FormGroup({
    date: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    time: new FormControl('', { nonNullable: true }),
  });

  ngOnInit(): void {
    const occurrence = this.occurrence();
    this.form.setValue({
      date: scheduledDay(occurrence),
      // Vide pour une heure automatique : l'enregistrer telle quelle la laisse automatique.
      time: occurrence.pinned ? scheduledTime(occurrence) : '',
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  /** N'émet que si quelque chose change : réenvoyer l'état courant re-répartirait le jour pour rien. */
  onSubmit(): void {
    if (this.saving()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const occurrence = this.occurrence();
    const { date, time } = this.form.getRawValue();
    const requested = time || null;
    const current = occurrence.pinned ? scheduledTime(occurrence) : null;

    if (date === scheduledDay(occurrence) && requested === current) {
      this.closed.emit();
      return;
    }

    this.rescheduled.emit({ occurrence, date, time: requested });
  }

  onCancel(): void {
    if (this.saving()) {
      return;
    }

    if (!this.confirmingCancel()) {
      this.confirmingCancel.set(true);
      return;
    }

    this.cancelled.emit(this.occurrence());
  }
}
