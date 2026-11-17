import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  OnInit,
  output,
  signal,
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
import { RepeatFormValue, ScheduleFormValue, ScheduleSlotValue } from "../../occurrence.model";
import { createRepeatGroup, RepeatFieldsComponent, repeatRule, repeatSummary } from "../repeat-fields/repeat-fields.component";
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
 * le 4/12, le 5/02…), ou selon une fréquence (« Répéter » : chaque lundi, tous
 * les 3 mois…).
 *
 * Pour chaque jour, l'heure est facultative : laissée vide, le back choisit le
 * créneau dans la fenêtre de publication du jour, en répartissant les
 * publications ; saisie, elle est fixée et ne bougera plus.
 */
@Component({
  selector: 'app-schedule-occurrence-modal',
  imports: [ReactiveFormsModule, LoaderComponent, RepeatFieldsComponent],
  templateUrl: './schedule-occurrence-modal.component.html',
  styleUrl: './schedule-occurrence-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleOccurrenceModalComponent implements OnInit {
  readonly publication = input.required<Publication>();

  /** Fin de la campagne de la publication (`AAAA-MM-JJ`) : fin proposée d'une répétition. */
  readonly campaignEnd = input<string | null>(null);

  /** Vrai tant que la requête de programmation est en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly submitted = output<ScheduleFormValue>();
  readonly repeatSubmitted = output<RepeatFormValue>();

  /** « Dates précises » (une ligne par jour) ou « Répéter » (une fréquence). */
  protected readonly mode = signal<'dates' | 'repeat'>('dates');

  readonly repeat = createRepeatGroup();

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

  ngOnInit(): void {
    const end = this.campaignEnd();

    // Par défaut, une répétition s'arrête avec la campagne, si elle n'est pas déjà finie.
    if (end && end >= this.minDate) {
      this.repeat.patchValue({ endKind: 'until', until: end });
    }
  }

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

    if (this.mode() === 'repeat') {
      this.submitRepeat();
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

  private submitRepeat(): void {
    const channel = this.form.controls.facebook;

    if (this.repeat.invalid || channel.invalid) {
      this.repeat.markAllAsTouched();
      channel.markAsTouched();
      return;
    }

    const { startDate, time } = this.repeat.getRawValue();
    this.repeatSubmitted.emit({
      startDate,
      time,
      rule: repeatRule(this.repeat),
      channels: ['FACEBOOK'],
      summary: repeatSummary(this.repeat),
    });
  }
}
