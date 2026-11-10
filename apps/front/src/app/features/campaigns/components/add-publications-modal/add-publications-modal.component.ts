import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  input,
  output,
  signal,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Publication } from "../../../publications/publication.model";
import { Campaign } from "../../campaign.model";

/**
 * Choix des publications à rattacher à une campagne. Celles d'une autre
 * campagne sont proposées aussi, en le signalant : les ajouter les déplace.
 */
@Component({
  selector: 'app-add-publications-modal',
  imports: [LoaderComponent],
  templateUrl: './add-publications-modal.component.html',
  styleUrl: './add-publications-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddPublicationsModalComponent {
  readonly campaign = input.required<Campaign>();
  /** Toutes les publications ; `null` tant qu'elles se chargent. */
  readonly publications = input<Publication[] | null>(null);
  /** Vrai tant que les rattachements sont en vol. */
  readonly saving = input(false);

  readonly closed = output<void>();
  /** Identifiants des publications à rattacher. */
  readonly submitted = output<number[]>();

  protected readonly query = signal('');
  protected readonly selected = signal<ReadonlySet<number>>(new Set());

  /** Celles qui ne sont pas déjà dans la campagne, filtrées par la recherche. */
  protected readonly candidates = computed(() => {
    const campaignId = this.campaign().id;
    const query = this.query().trim().toLowerCase();

    return (this.publications() ?? [])
      .filter((publication) => publication.campaign?.id !== campaignId)
      .filter((publication) => !query || publication.content.toLowerCase().includes(query));
  });

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  toggle(publicationId: number): void {
    this.selected.update((selected) => {
      const next = new Set(selected);
      if (!next.delete(publicationId)) {
        next.add(publicationId);
      }
      return next;
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onSubmit(): void {
    if (this.saving() || this.selected().size === 0) {
      return;
    }

    this.submitted.emit([...this.selected()]);
  }
}
