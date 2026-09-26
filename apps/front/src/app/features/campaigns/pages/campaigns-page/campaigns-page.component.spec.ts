import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { Campaign } from '../../campaign.model';
import { CampaignsPageComponent } from './campaigns-page.component';

registerLocaleData(localeFr, 'fr-FR');

function campaign(overrides: Partial<Campaign> = {}): Campaign {
  return {
    id: 1,
    name: 'Marché de Noël',
    description: 'Toutes les annonces du marché',
    startDate: '2026-12-01T00:00:00',
    endDate: '2026-12-24T00:00:00',
    status: 'SCHEDULED',
    publications: [],
    ...overrides,
  };
}

describe('CampaignsPageComponent', () => {
  let fixture: ComponentFixture<CampaignsPageComponent>;
  let compiled: HTMLElement;
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;

  /** Crée la page et satisfait la requête de chargement initiale. */
  function render(campaigns: Campaign[] = [campaign()]): void {
    fixture = TestBed.createComponent(CampaignsPageComponent);
    fixture.detectChanges();
    httpTesting.expectOne('/api/campaigns').flush(campaigns);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function openCreateModal(): void {
    compiled.querySelector<HTMLButtonElement>('.campaign-list-create')!.click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should render the loaded campaigns', () => {
    render([campaign(), campaign({ id: 2, name: 'Budget participatif' })]);

    expect(compiled.querySelectorAll('.campaign-list-item').length).toBe(2);
  });

  it('should show a loader while the list is in flight', () => {
    fixture = TestBed.createComponent(CampaignsPageComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-loader')).not.toBeNull();

    httpTesting.expectOne('/api/campaigns').flush([]);
  });

  it('should select the first campaign so the detail is never empty on arrival', () => {
    render([campaign({ id: 3, name: 'Budget participatif' }), campaign({ id: 4 })]);

    expect(compiled.querySelector('app-campaign-detail h2')?.textContent?.trim())
      .toBe('Budget participatif');
  });

  it('should show the campaign clicked in the list', () => {
    render([campaign({ id: 3 }), campaign({ id: 4, name: 'Budget participatif' })]);

    compiled.querySelectorAll<HTMLButtonElement>('.campaign-list-item')[1].click();
    fixture.detectChanges();

    expect(compiled.querySelector('app-campaign-detail h2')?.textContent?.trim())
      .toBe('Budget participatif');
  });

  it('should hide the detail when there is no campaign at all', () => {
    render([]);

    expect(compiled.querySelector('app-campaign-detail')).toBeNull();
  });

  it('should report a loading failure', () => {
    fixture = TestBed.createComponent(CampaignsPageComponent);
    fixture.detectChanges();
    httpTesting.expectOne('/api/campaigns').flush('boom', { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.campaigns-error')?.textContent)
      .toContain('n’ont pas pu être chargées');
  });

  describe('création', () => {
    function submitForm(): void {
      const modal = compiled.querySelector('app-create-campaign-modal')!;
      (modal.querySelector('#campaign-name') as HTMLInputElement).value = 'Fête de la musique';
      modal.querySelector('#campaign-name')!.dispatchEvent(new Event('input'));
      (modal.querySelector('#campaign-start') as HTMLInputElement).value = '2026-06-01';
      modal.querySelector('#campaign-start')!.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      modal.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    }

    it('should open the modal from the + button', () => {
      render();
      openCreateModal();

      expect(compiled.querySelector('app-create-campaign-modal')).not.toBeNull();
    });

    /** Le back attend un `LocalDateTime` là où l'input rend `AAAA-MM-JJ`. */
    it('should POST the campaign with midnight appended to the dates', () => {
      render();
      openCreateModal();
      submitForm();

      const request = httpTesting.expectOne('/api/campaigns');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        name: 'Fête de la musique',
        description: '',
        startDate: '2026-06-01T00:00:00',
        endDate: null,
      });

      request.flush(campaign({ id: 9, name: 'Fête de la musique' }));
    });

    it('should prepend the created campaign, select it and close the modal', () => {
      const success = vi.spyOn(notifications, 'success');
      render();
      openCreateModal();
      submitForm();
      httpTesting.expectOne('/api/campaigns')
                 .flush(campaign({ id: 9, name: 'Fête de la musique' }));
      fixture.detectChanges();

      expect(compiled.querySelectorAll('.campaign-list-item').length).toBe(2);
      expect(compiled.querySelector('app-create-campaign-modal')).toBeNull();
      expect(compiled.querySelector('app-campaign-detail h2')?.textContent?.trim())
        .toBe('Fête de la musique');
      expect(success).toHaveBeenCalledWith('Campagne créée.');
    });

    it('should keep the modal open and warn when the creation fails', () => {
      const error = vi.spyOn(notifications, 'error');
      render();
      openCreateModal();
      submitForm();
      httpTesting.expectOne('/api/campaigns')
                 .flush('boom', { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();

      expect(compiled.querySelector('app-create-campaign-modal')).not.toBeNull();
      expect(error).toHaveBeenCalledWith('La campagne n’a pas pu être créée.');
    });
  });
});
