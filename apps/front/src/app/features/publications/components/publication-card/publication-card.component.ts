import { DatePipe, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  OutputEmitterRef,
  signal,
} from '@angular/core';
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { MediasService } from "../../../medias/medias.service";
import { isPublishableMedia, Publication } from "../../publication.model";

@Component({
  selector: 'app-publication-card',
  imports: [DatePipe, NgTemplateOutlet, LoaderComponent],
  templateUrl: './publication-card.component.html',
  styleUrl: './publication-card.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'closeMenu()',
  },
})
export class PublicationCardComponent {
  private readonly mediasService = inject(MediasService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly publication = input.required<Publication>();

  /** Diffusion Facebook en cours : le bouton passe en attente et se verrouille. */
  readonly publishing = input(false);

  /** Suppression en cours : les boutons de suppression se verrouillent. */
  readonly deleting = input(false);

  /** Droits du compte : une action qu'il ne peut pas faire n'est pas proposée. */
  readonly canEdit = input(true);
  readonly canDelete = input(true);
  readonly canPublish = input(true);
  readonly canSchedule = input(true);
  /** Choisir des visuels exige aussi de voir la médiathèque. */
  readonly canManageMedias = input(true);
  /** Choisir une campagne exige aussi de voir les campagnes. */
  readonly canAssignCampaign = input(true);

  readonly generateXlsx = output<Publication>();
  readonly manageMedias = output<Publication>();
  readonly publishOnFacebook = output<Publication>();
  readonly assignCampaign = output<Publication>();
  readonly schedule = output<Publication>();
  readonly edit = output<Publication>();
  readonly remove = output<Publication>();

  /**
   * Suppression en deux temps, comme dans la médiathèque : le geste reste sur
   * la carte, sans `confirm()` natif.
   */
  protected readonly confirmingDelete = signal(false);

  protected confirmDelete(): void {
    this.confirmingDelete.set(false);
    this.remove.emit(this.publication());
  }

  /** Menu déroulant « Diffuser » (publier, programmer, exporter) ouvert ou non. */
  protected readonly openMenu = signal<'diffuse' | null>(null);

  protected toggleMenu(menu: 'diffuse'): void {
    this.openMenu.update((current) => (current === menu ? null : menu));
  }

  protected closeMenu(): void {
    this.openMenu.set(null);
  }

  /** Un clic hors de la carte referme le menu ouvert. */
  protected onDocumentClick(event: MouseEvent): void {
    if (this.openMenu() && !this.host.nativeElement.contains(event.target as Node)) {
      this.closeMenu();
    }
  }

  /** Choix d'une entrée de menu : le menu se referme, l'action part. */
  protected pick(action: OutputEmitterRef<Publication>): void {
    this.closeMenu();
    action.emit(this.publication());
  }

  protected askDelete(): void {
    this.closeMenu();
    this.confirmingDelete.set(true);
  }

  /** Le compte peut diffuser d'une façon ou d'une autre : le bouton « Diffuser » a un sens. */
  readonly canDiffuse = computed(() => this.canPublish() || this.canSchedule());

  /**
   * La publication n'a pas de titre propre : sa première ligne non vide en
   * tient lieu, le reste du texte forme l'extrait. Un texte d'un seul bloc est
   * coupé après sa première phrase, si elle est assez courte pour un titre.
   */
  private readonly headline = computed(() => {
    const lines = (this.publication().content ?? '')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);

    if (lines.length === 0) {
      return { title: 'Publication sans texte', excerpt: '' };
    }
    if (lines.length > 1) {
      return { title: lines[0], excerpt: lines.slice(1).join(' ') };
    }

    const sentence = /^(.{8,120}?[.!?…])\s+(\S.*)$/s.exec(lines[0]);
    return sentence
      ? { title: sentence[1], excerpt: sentence[2] }
      : { title: lines[0], excerpt: '' };
  });

  readonly title = computed(() => this.headline().title);
  readonly excerpt = computed(() => this.headline().excerpt);

  readonly verified = computed(() => this.publication().status === 'VERIFIED');
  readonly campaignName = computed(() => this.publication().campaign?.name ?? null);

  /**
   * « modifiée par bruno », ou « modifiée » si c'est l'auteur lui-même ; rien si
   * la publication n'a pas bougé depuis sa création.
   */
  readonly modification = computed(() => {
    const publication = this.publication();

    if (!publication.updatedBy || publication.updatedAt === publication.createdAt) {
      return null;
    }

    return publication.updatedBy === publication.createdBy ? 'modifiée' : `modifiée par ${ publication.updatedBy }`;
  });

  readonly mediaCount = computed(() => this.publication().medias?.length ?? 0);

  /**
   * Vignette : la première **image** rattachée. Un PDF placé en tête ne peut pas
   * en servir, la carte retombe alors sur l'icône « sans média ».
   */
  private readonly thumbnail = computed(
    () => this.publication().medias?.find(isPublishableMedia) ?? null,
  );

  readonly thumbnailUrl = computed(() => {
    const media = this.thumbnail();
    return media ? this.mediasService.fileUrl(media.id) : null;
  });

  /**
   * Vide en l'absence de description : la vignette n'apporte alors rien qu'un
   * lecteur d'écran puisse annoncer, le contenu de la publication étant lu juste
   * en dessous.
   */
  readonly thumbnailAlt = computed(() => this.thumbnail()?.altText?.trim() ?? '');
}
