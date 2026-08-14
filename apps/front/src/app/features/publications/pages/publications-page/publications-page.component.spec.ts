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
  { id: 1, content: 'Marché de producteurs samedi', status: 'VERIFIED', campaign: { id: 7, name: 'Parcs & Loisirs' } },
  { id: 2, content: 'Conseil municipal reporté', status: 'DRAFT' },
  { id: 3, content: 'Inscriptions au centre de loisirs', status: 'DRAFT' },
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
    request.flush({ id: 9, content: 'Nouvelle annonce', status: 'VERIFIED' });
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

  it.each([
    ['.action-xlsx'],
    ['.action-facebook'],
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
});
