import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  output,
} from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from "@angular/forms";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Publication } from "../../../publications/publication.model";
import { ScheduleFormValue, ScheduleSlotValue } from "../../occurrence.model";
import { toIsoDate } from "../../occurrence.utils";

/** Plafond du back (`CreatePublicationOccurrencesRequest.MAX_SLOTS`). */
export const MAX_SLOTS = 60;

type SlotGroup = FormGroup<{ date: FormControl<string>, time: FormControl<string> }>;

/**
 * Deux lignes identiques (même jour sans heure, ou même jour et même heure)
 * programmeraient deux fois le même post : le back les refuse, on le dit avant.
 */
function distinctSlots(control: AbstractControl): ValidationErrors | null {
  const keys = (control.value as ScheduleSlotValue[])
    .filter((slot) => slot.date)
    .map((slot) => `${ slot.date }T${ slot.time }`);

  return new Set(keys).size === keys.length ? null : { duplicate: true };
}

/**
 * Programme la diffusion d'une publication, à un ou plusieurs jours (le 11/11,
 * le 4/12, le 5/02…).
 *
 * Pour chaque jour, l'heure est facultative : laissée vide, le back choisit le
 * créneau dans la fenêtre de publication du jour, en répartissant les
 * publications ; saisie, elle est fixée et ne bougera plus.
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

  protected readonly maxSlots = MAX_SLOTS;

  readonly form = new FormGroup({
    slots: new FormArray<SlotGroup>([ScheduleOccurrenceModalComponent.slot()], { validators: [distinctSlots] }),
    facebook: new FormControl(true, { nonNullable: true, validators: [Validators.requiredTrue] }),
  });

  get slots(): FormArray<SlotGroup> {
    return this.form.controls.slots;
  }

  private static slot(): SlotGroup {
    return new FormGroup({
      date: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      time: new FormControl('', { nonNullable: true }),
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  addSlot(): void {
    if (this.slots.length < MAX_SLOTS) {
      this.slots.push(ScheduleOccurrenceModalComponent.slot());
    }
  }

  /** La dernière ligne reste : sans jour, il n'y a rien à programmer. */
  removeSlot(index: number): void {
    if (this.slots.length > 1) {
      this.slots.removeAt(index);
    }
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

    // Dans l'ordre du calendrier, quel que soit l'ordre de saisie : le back
    // répond dans cet ordre, et l'annonce de la programmation le suit. Un jour
    // sans heure passe avant une heure fixée le même jour (« » < « 17:45 »).
    const slots = [...this.slots.getRawValue()]
      .sort((a, b) => `${ a.date }T${ a.time }`.localeCompare(`${ b.date }T${ b.time }`));

    this.submitted.emit({ slots, channels: ['FACEBOOK'] });
  }
}
