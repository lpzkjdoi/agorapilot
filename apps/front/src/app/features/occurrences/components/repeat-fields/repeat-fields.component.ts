import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  CustomRepeat,
  describeRepeat,
  Frequency,
  MONTH_LABELS,
  ORDINAL_LABELS,
  ORDINALS,
  PRESET_LABELS,
  RepeatEnd,
  RepeatPreset,
  toRRule,
  WEEKDAY_LABELS,
  Weekday,
  WEEKDAYS,
} from '../../recurrence';

export type EndKind = RepeatEnd['kind'];

export type RepeatGroup = FormGroup<{
  startDate: FormControl<string>
  time: FormControl<string>
  preset: FormControl<RepeatPreset>
  frequency: FormControl<Frequency>
  interval: FormControl<number>
  weekdays: FormControl<Weekday[]>
  monthMode: FormControl<'days' | 'ordinal'>
  monthDays: FormControl<number[]>
  ordinal: FormControl<number>
  ordinalDay: FormControl<Weekday>
  months: FormControl<number[]>
  endKind: FormControl<EndKind>
  until: FormControl<string>
  count: FormControl<number>
}>;

/** Une date de fin doit suivre le départ ; une fin « le … » doit avoir sa date. */
function endAfterStart(control: AbstractControl): ValidationErrors | null {
  const { startDate, endKind, until } = (control as RepeatGroup).getRawValue();

  if (endKind !== 'until') {
    return null;
  }
  if (!until) {
    return { untilMissing: true };
  }

  return startDate && until < startDate ? { endBeforeStart: true } : null;
}

/** Le formulaire « Répéter », par défaut chaque semaine, sans fin (ou jusqu'à `until`). */
export function createRepeatGroup(until: string | null = null): RepeatGroup {
  const control = <T>(value: T, validators: ValidatorFn[] = []) =>
    new FormControl<T>(value, { nonNullable: true, validators });

  return new FormGroup({
    startDate: control('', [Validators.required]),
    time: control(''),
    preset: control<RepeatPreset>('WEEKLY'),
    frequency: control<Frequency>('WEEKLY'),
    interval: control(1, [Validators.required, Validators.min(1), Validators.max(999)]),
    weekdays: control<Weekday[]>([]),
    monthMode: control<'days' | 'ordinal'>('days'),
    monthDays: control<number[]>([]),
    ordinal: control(1),
    ordinalDay: control<Weekday>('MO'),
    months: control<number[]>([]),
    endKind: control<EndKind>(until ? 'until' : 'never'),
    until: control(until ?? ''),
    count: control(10, [Validators.required, Validators.min(1), Validators.max(999)]),
  }, { validators: [endAfterStart] }) as RepeatGroup;
}

function choices(group: RepeatGroup): { preset: RepeatPreset, custom: CustomRepeat, end: RepeatEnd } {
  const value = group.getRawValue();
  const end: RepeatEnd = value.endKind === 'until'
    ? { kind: 'until', date: value.until }
    : value.endKind === 'count' ? { kind: 'count', count: value.count } : { kind: 'never' };

  return { preset: value.preset, custom: value, end };
}

/** La règle `RRULE` saisie. */
export function repeatRule(group: RepeatGroup): string {
  const { preset, custom, end } = choices(group);
  return toRRule(preset, custom, end);
}

/** La répétition saisie, en une phrase. */
export function repeatSummary(group: RepeatGroup): string {
  const { preset, custom, end } = choices(group);
  const { startDate, time } = group.getRawValue();
  return describeRepeat(preset, custom, end, startDate, time);
}

/**
 * Le bloc « Répéter » de la modale « Programmer » : départ, heure facultative,
 * préréglage ou personnalisation (fréquence, « tous les N », jours de la
 * semaine, jours du mois ou « le premier lundi », mois de l'année), et fin.
 *
 * Il n'émet rien : la modale tient le formulaire et l'envoie.
 */
@Component({
  selector: 'app-repeat-fields',
  imports: [ReactiveFormsModule],
  templateUrl: './repeat-fields.component.html',
  styleUrl: './repeat-fields.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepeatFieldsComponent {
  readonly form = input.required<RepeatGroup>();
  readonly minDate = input.required<string>();

  protected readonly presets = Object.entries(PRESET_LABELS) as [RepeatPreset, string][];
  protected readonly weekdays = WEEKDAYS;
  protected readonly weekdayLabels = WEEKDAY_LABELS;
  protected readonly monthLabels = MONTH_LABELS;
  protected readonly monthShort = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
  protected readonly ordinals = ORDINALS;
  protected readonly ordinalLabels = ORDINAL_LABELS;
  protected readonly monthDays = Array.from({ length: 31 }, (_, i) => i + 1);
  protected readonly units: [Frequency, string][] = [
    ['DAILY', 'jour(s)'], ['WEEKLY', 'semaine(s)'], ['MONTHLY', 'mois'], ['YEARLY', 'an(s)'],
  ];

  protected summary(): string {
    return repeatSummary(this.form());
  }

  protected has(control: 'weekdays' | 'monthDays' | 'months', value: Weekday | number): boolean {
    return (this.form().controls[control].value as (Weekday | number)[]).includes(value);
  }

  /** Coche ou décoche un jour, un quantième ou un mois. */
  protected toggle(control: 'weekdays' | 'monthDays' | 'months', value: Weekday | number): void {
    const field = this.form().controls[control] as FormControl<(Weekday | number)[]>;
    const current = field.value;
    field.setValue(current.includes(value) ? current.filter((v) => v !== value) : [...current, value]);
  }
}
