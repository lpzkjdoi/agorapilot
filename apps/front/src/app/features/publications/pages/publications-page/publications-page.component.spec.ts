import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { Campaign } from '../../../campaigns/campaign.model';
import { Publication } from '../../publication.model';
import { PublicationsPageComponent } from './publications-page.component';

registerLocaleData(localeFr, 'fr-FR');

const publications: Publication[] = [
  { id: 1, content: 'Marché de producteurs samedi', status: 'VERIFIED', campaign: { id: 7, name: 'Parcs & Loisirs' }, medias: [] },
  { id: 2, content: 'Conseil municipal reporté', status: 'DRAFT', medias: [] },
  { id: 3, content: 'Inscriptions au centre de loisirs', status: 'DRAFT', medias: [] },
];

const campaigns: Campaign[] = [
  {
    id: 7,
    name: 'Parcs & Loisirs',
    description: 'Programme du printemps',
    startDate: null,
    endDate: null,
    status: 'ACTIVE',
    publications: [],
  },
];

describe('PublicationsPageComponent', () => {
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicationsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    // Les messages transitoires passent par le portal de notifications, monté
    // hors de la page : on les vérifie sur le service plutôt que dans le DOM.
    notifications = TestBed.inject(NotificationService);
  });

  /** Crée la page et répond aux deux requêtes émises à l'initialisation. */
  function render(
    list: Publication[] = publications,
  ): ComponentFixture<PublicationsPageComponent> {
    const fixture = TestBed.createComponent(PublicationsPageComponent);

    httpTesting.expectOne('/api/publications').flush(list);
    httpTesting.expectOne('/api/campaigns').flush(campaigns);

    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpTesting.verify();
  });

  it('should create and load publications and campaigns on init', () => {
    const fixture = render();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the header and the create button', () => {
    const compiled = render().nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toBe('Publications');
    expect(compiled.querySelector('.publications-subtitle')?.textContent).toContain(
      'Gérez et organisez vos publications',
    );
    expect(compiled.querySelector('.publications-create')?.textContent).toContain(
      'Créer une publication',
    );
  });

  it('should render one card per publication', () => {
    const compiled = render().nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('app-publication-card').length).toBe(3);
    expect(compiled.querySelector('.publications-count')?.textContent).toContain('3 publications');
  });

  it('should show `Aucune publication disponible` when the list is empty', () => {
    const compiled = render([]).nativeElement as HTMLElement;

    expect(compiled.querySelector('.publications-placeholder')?.textContent?.trim()).toBe(
      'Aucune publication disponible',
    );
    expect(compiled.querySelector('app-publication-card')).toBeNull();
  });

  it('should compute the counters handed over to the filters bar', () => {
    const compiled = render().nativeElement as HTMLElement;
    const stats = Array.from(compiled.querySelectorAll('.filters-stats li')).map(
      (item) => item.textContent?.replace(/\s+/g, ' ').trim(),
    );

    expect(stats).toEqual([
      'Total : 3',
      'Vérifiées : 1',
      'Brouillons : 2',
      'Dans campagnes : 1',
    ]);
  });

  it('should filter the cards on the search field', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    const search = compiled.querySelector('#publications-search') as HTMLInputElement;
    search.value = 'conseil';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-publication-card').length).toBe(1);
    expect(compiled.querySelector('.publications-count')?.textContent).toContain('1 publication');
  });

  it('should filter the cards on the status select', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    const status = compiled.querySelector('#publications-status') as HTMLSelectElement;
    status.value = 'VERIFIED';
    status.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-publication-card').length).toBe(1);
  });

  it('should list the loaded campaigns in the campaign filter and filter on them', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    const campaign = compiled.querySelector('#publications-campaign') as HTMLSelectElement;
    const options = Array.from(campaign.options).map((option) => option.textContent?.trim());
    expect(options).toEqual(['Toutes les campagnes', 'Sans campagne', 'Parcs & Loisirs']);

    campaign.value = 'NONE';
    campaign.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(compiled.querySelectorAll('app-publication-card').length).toBe(2);

    campaign.value = '7';
    campaign.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(compiled.querySelectorAll('app-publication-card').length).toBe(1);
  });

  it('should tell the user when the filters exclude everything', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    const search = compiled.querySelector('#publications-search') as HTMLInputElement;
    search.value = 'introuvable';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(compiled.querySelector('.publications-placeholder')?.textContent?.trim()).toBe(
      'Aucune publication ne correspond à ces filtres',
    );
  });

  it('should open the modal, POST the new publication and prepend it to the list', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.publications-create')?.click();
    fixture.detectChanges();
    expect(compiled.querySelector('app-create-publication-modal')).not.toBeNull();

    const textarea = compiled.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Nouvelle annonce';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    compiled.querySelector('form')?.dispatchEvent(new Event('submit'));

    const request = httpTesting.expectOne('/api/publications');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ content: 'Nouvelle annonce', status: 'VERIFIED' });
    request.flush({ id: 9, content: 'Nouvelle annonce', status: 'VERIFIED', medias: [] });
    fixture.detectChanges();

    expect(compiled.querySelector('app-create-publication-modal')).toBeNull();
    expect(compiled.querySelectorAll('app-publication-card').length).toBe(4);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'success', message: 'Publication créée.' }),
    ]);
  });

  it('should close the modal without any request when it is dismissed', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.publications-create')?.click();
    fixture.detectChanges();

    compiled.querySelector<HTMLButtonElement>('.modal-close')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('app-create-publication-modal')).toBeNull();
    httpTesting.expectNone('/api/publications');
  });

  it('should publish on Facebook and confirm it', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.action-facebook')?.click();
    fixture.detectChanges();

    // Le bouton de la carte concernée se verrouille le temps de la diffusion.
    const button = compiled.querySelector<HTMLButtonElement>('.action-facebook');
    expect(button?.disabled).toBe(true);
    expect(button?.textContent).toContain('Publication…');

    const request = httpTesting.expectOne('/api/publications/1/deliveries');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ channel: 'FACEBOOK' });

    request.flush({
      id: 99,
      occurrenceId: 42,
      channel: 'FACEBOOK',
      status: 'PUBLISHED',
      publishedAt: '2026-08-22T10:00:00',
      externalId: '123_456',
    });
    fixture.detectChanges();

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({
        level: 'success',
        message: 'Publication diffusée sur Facebook.',
      }),
    ]);
    expect(
      compiled.querySelector<HTMLButtonElement>('.action-facebook')?.disabled,
    ).toBe(false);
  });

  it('should relay the reason returned with the 502 when Facebook refuses the post', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.action-facebook')?.click();
    fixture.detectChanges();

    httpTesting.expectOne('/api/publications/1/deliveries').flush(
      { message: 'No token available', code: 502 },
      { status: 502, statusText: 'Bad Gateway' },
    );
    fixture.detectChanges();

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({
        level: 'error',
        message: 'La publication n’a pas pu être diffusée sur Facebook : No token available',
      }),
    ]);
    // L'échec débloque la carte : l'utilisateur peut réessayer.
    expect(
      compiled.querySelector<HTMLButtonElement>('.action-facebook')?.disabled,
    ).toBe(false);
  });

  it('should not fire a second request while a publication is already in flight', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.action-facebook')?.click();
    fixture.detectChanges();
    compiled.querySelector<HTMLButtonElement>('.action-facebook')?.click();
    fixture.detectChanges();

    httpTesting.expectOne('/api/publications/1/deliveries').flush({
      id: 99,
      occurrenceId: 42,
      channel: 'FACEBOOK',
      status: 'PUBLISHED',
      publishedAt: '2026-08-22T10:00:00',
      externalId: '123_456',
    });
  });

  it.each([
    ['.action-xlsx'],
    ['.action-campaign'],
  ])('should warn that the action %s has no endpoint yet', (selector) => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>(selector)?.click();
    fixture.detectChanges();

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({
        level: 'warning',
        message: 'Cette action n’est pas encore disponible.',
      }),
    ]);
    // Aucune requête n'est émise : les endpoints n'existent pas encore.
    httpTesting.expectNone('/api/publications');
  });

  it('should show the loader while the publications are being fetched', () => {
    const fixture = TestBed.createComponent(PublicationsPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.publications-loading app-loader')).not.toBeNull();
    expect(compiled.querySelector('app-publication-card')).toBeNull();

    httpTesting.expectOne('/api/publications').flush(publications);
    httpTesting.expectOne('/api/campaigns').flush(campaigns);
    fixture.detectChanges();

    expect(compiled.querySelector('.publications-loading')).toBeNull();
    expect(compiled.querySelectorAll('app-publication-card').length).toBe(3);
  });

  it('should show the loader in the submit button while the creation is in flight', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.publications-create')?.click();
    fixture.detectChanges();

    const textarea = compiled.querySelector<HTMLTextAreaElement>('#content') as HTMLTextAreaElement;
    textarea.value = 'Nouvelle annonce';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const submit = compiled.querySelector<HTMLButtonElement>('.publication-form-submit');
    submit?.click();
    fixture.detectChanges();

    expect(submit?.querySelector('app-loader')).not.toBeNull();
    expect(submit?.disabled).toBe(true);
    expect(
      compiled.querySelector<HTMLButtonElement>('.publication-form-cancel')?.disabled,
    ).toBe(true);

    httpTesting
      .expectOne('/api/publications')
      .flush({ id: 9, content: 'Nouvelle annonce', status: 'VERIFIED', medias: [] });
    fixture.detectChanges();

    // La modale part avec le loader une fois la publication créée.
    expect(compiled.querySelector('app-create-publication-modal')).toBeNull();
  });

  it('should release the submit button when the creation fails', () => {
    const fixture = render();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.publications-create')?.click();
    fixture.detectChanges();

    const textarea = compiled.querySelector<HTMLTextAreaElement>('#content') as HTMLTextAreaElement;
    textarea.value = 'Nouvelle annonce';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    compiled.querySelector<HTMLButtonElement>('.publication-form-submit')?.click();
    fixture.detectChanges();

    httpTesting.expectOne('/api/publications').flush('boom', {
      status: 500,
      statusText: 'Server Error',
    });
    fixture.detectChanges();

    const submit = compiled.querySelector<HTMLButtonElement>('.publication-form-submit');
    expect(submit?.disabled).toBe(false);
    expect(submit?.querySelector('app-loader')).toBeNull();
  });

  it('should surface a loading error instead of the list', () => {
    const fixture = TestBed.createComponent(PublicationsPageComponent);

    httpTesting.expectOne('/api/publications').flush('boom', {
      status: 500,
      statusText: 'Server Error',
    });
    httpTesting.expectOne('/api/campaigns').flush(campaigns);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.publications-error')?.textContent).toContain(
      'Les publications n’ont pas pu être chargées.',
    );
    expect(compiled.querySelector('app-publication-card')).toBeNull();
  });

  // ------------------------------- Visuels -------------------------------

  describe('media picker', () => {
    const libraryMedia = {
      id: 4,
      title: 'Affiche du marché',
      originalFilename: 'affiche.png',
      contentType: 'image/png',
      sizeBytes: 1024,
      width: 1200,
      height: 1800,
      altText: null,
      checksum: 'cafe',
      archived: false,
      createdAt: '2026-08-23T10:00:00',
      updatedAt: '2026-08-23T10:00:00',
    };

    /** Rend la page puis ouvre le sélecteur sur la première publication. */
    function open(): ComponentFixture<PublicationsPageComponent> {
      const fixture = render();
      const compiled = fixture.nativeElement as HTMLElement;

      (compiled.querySelectorAll('.action-medias')[0] as HTMLButtonElement).click();
      fixture.detectChanges();
      return fixture;
    }

    function pickFirstAndSave(fixture: ComponentFixture<PublicationsPageComponent>): void {
      const compiled = fixture.nativeElement as HTMLElement;

      (compiled.querySelector('.picker-item') as HTMLButtonElement).click();
      fixture.detectChanges();
      (compiled.querySelector('.modal-actions .primary') as HTMLButtonElement).click();
    }

    it('should load the library the first time the picker is opened', () => {
      const fixture = open();

      const request = httpTesting.expectOne('/api/medias?archived=false');
      expect(request.request.method).toBe('GET');
      request.flush([libraryMedia]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('app-media-picker-modal')).not.toBeNull();
      expect(compiled.querySelectorAll('.picker-item').length).toBe(1);
    });

    it('should not reload the library on a second opening', () => {
      const fixture = open();
      httpTesting.expectOne('/api/medias?archived=false').flush([libraryMedia]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      (compiled.querySelector('.modal-close') as HTMLButtonElement).click();
      fixture.detectChanges();
      (compiled.querySelectorAll('.action-medias')[0] as HTMLButtonElement).click();
      fixture.detectChanges();

      // La médiathèque ne change pas pendant qu'on parcourt la page.
      httpTesting.expectNone('/api/medias?archived=false');
    });

    it('should PUT the selection and refresh the card', () => {
      const fixture = open();
      httpTesting.expectOne('/api/medias?archived=false').flush([libraryMedia]);
      fixture.detectChanges();

      pickFirstAndSave(fixture);

      const request = httpTesting.expectOne('/api/publications/1/medias');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ mediaIds: [4] });

      request.flush({ ...publications[0], medias: [libraryMedia] });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('app-media-picker-modal')).toBeNull();
      expect(compiled.querySelector('.media-thumbnail')?.getAttribute('src')).toBe('/api/medias/4/file');
      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'success', message: 'Visuels enregistrés.' }),
      ]);
    });

    it('should report a failed attachment without closing the picker', () => {
      const fixture = open();
      httpTesting.expectOne('/api/medias?archived=false').flush([libraryMedia]);
      fixture.detectChanges();

      pickFirstAndSave(fixture);

      httpTesting.expectOne('/api/publications/1/medias')
                 .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).querySelector('app-media-picker-modal')).not.toBeNull();
      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'error' }),
      ]);
    });

    it('should tell the user when the library cannot be loaded', () => {
      const fixture = open();
      httpTesting.expectOne('/api/medias?archived=false')
                 .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'error', message: expect.stringContaining('médiathèque') }),
      ]);
    });
  });
});
