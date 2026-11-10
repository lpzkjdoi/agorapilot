import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  input,
  OnInit,
  output,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from "@angular/forms";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Campaign, CampaignFormValue } from "../../campaign.model";

/** La date de fin ne précède pas la date de début ; une période ouverte est toujours valide. */
function orderedPeriod(group: AbstractControl): ValidationErrors | null {
  const { startDate, endDate } = group.value as CampaignFormValue;
  return startDate && endDate && endDate < startDate ? { periodReversed: true } : null;
}

/**
 * Formulaire de campagne, en création ou — quand `campaign` est fourni — en
 * modification.
 */
@Component({
  selector: 'app-campaign-form-modal',
  imports: [ReactiveFormsModule, LoaderComponent],
  templateUrl: './campaign-form-modal.component.html',
  styleUrl: './campaign-form-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignFormModalComponent implements OnInit {
  /** La campagne à modifier ; absente, le formulaire en crée une. */
  readonly campaign = input<Campaign | null>(null);
  /** Vrai tant que la requête d'enregistrement est en vol. */
  readonly saving = input(false);
  readonly closed = output<void>();
  readonly submitted = output<CampaignFormValue>();

  protected readonly editing = computed(() => this.campaign() !== null);

  /**
   * À la création, une campagne démarre au plus tôt aujourd'hui — le back
   * contrôle les dates à la journée. En modification, aucune borne : une
   * campagne en cours garde sa date de début passée.
   */
  protected readonly minDate = computed(() => this.editing() ? null : CampaignFormModalComponent.today());

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255)],
    }),
    description: new FormControl('', { nonNullable: true }),
    startDate: new FormControl('', { nonNullable: true }),
    endDate: new FormControl('', { nonNullable: true }),
  }, { validators: orderedPeriod });

  ngOnInit(): void {
    const campaign = this.campaign();

    if (campaign) {
      this.form.setValue({
        name: campaign.name,
        description: campaign.description ?? '',
        // `LocalDateTime` ISO : la date est ses dix premiers caractères.
        startDate: campaign.startDate?.slice(0, 10) ?? '',
        endDate: campaign.endDate?.slice(0, 10) ?? '',
      });
    }
  }

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
    if (this.saving()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitted.emit(this.form.getRawValue());
  }
}
