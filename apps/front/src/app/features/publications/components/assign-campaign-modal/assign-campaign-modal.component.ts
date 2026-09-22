import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Campaign } from "../../../campaigns/campaign.model";
import { Publication } from "../../publication.model";

/** Valeur du sélecteur : `NONE` détache la publication. */
const NONE = 'NONE';

@Component({
  selector: 'app-assign-campaign-modal',
  imports: [LoaderComponent],
  templateUrl: './assign-campaign-modal.component.html',
  styleUrl: './assign-campaign-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignCampaignModalComponent {
  readonly publication = input.required<Publication>();
  readonly campaigns = input.required<Campaign[]>();

  /** Vrai tant que la requête de rattachement est en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  readonly submitted = output<number | null>();

  protected readonly none = NONE;

  /** Sélection courante, réinitialisée si la modale change de publication. */
  protected readonly selected = linkedSignal<string>(
    () => this.publication().campaign?.id?.toString() ?? NONE,
  );

  protected readonly unchanged = computed(
    () => this.selected() === (this.publication().campaign?.id?.toString() ?? NONE),
  );

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  protected onSelect(event: Event): void {
    this.selected.set((event.target as HTMLSelectElement).value);
  }

  onSubmit(): void {
    // Un double envoi rejouerait le même rattachement : on ignore le second.
    if (this.saving()) {
      return;
    }

    const selected = this.selected();
    this.submitted.emit(selected === NONE ? null : Number(selected));
  }
}
