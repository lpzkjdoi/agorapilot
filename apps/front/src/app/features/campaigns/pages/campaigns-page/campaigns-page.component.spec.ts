import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { Publication } from '../../../publications/publication.model';
import { Campaign } from '../../campaign.model';
import { CampaignsPageComponent } from './campaigns-page.component';
import { provideSignedInAs, signInAs } from '../../../../core/auth/auth.testing';

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
      providers: [provideSignedInAs(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  /** Répond aux chargements de diffusions en attente — chaque sélection en déclenche un. */
  function flushPlanning(occurrences: Occurrence[] = []): void {
    fixture.detectChanges();
    httpTesting.match((request) => request.url.endsWith('/occurrences'))
               .filter((request) => !request.cancelled)
               .forEach((request) => request.flush(occurrences));
    fixture.detectChanges();
  }

  afterEach(() => {
    // Un changement de sélection annule la requête précédente (`switchMap`).
    httpTesting.match((request) => request.url.endsWith('/occurrences'))
               .filter((request) => !request.cancelled)
               .forEach((request) => request.flush([]));
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
      const modal = compiled.querySelector('app-campaign-form-modal')!;
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

      expect(compiled.querySelector('app-campaign-form-modal')).not.toBeNull();
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
      expect(compiled.querySelector('app-campaign-form-modal')).toBeNull();
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

      expect(compiled.querySelector('app-campaign-form-modal')).not.toBeNull();
      expect(error).toHaveBeenCalledWith('La campagne n’a pas pu être créée.');
    });
  });

  it('should list the campaigns without offering to create one, for a reader', () => {
    signInAs(['campaigns:read']);
    render();

    expect(compiled.querySelector('.campaign-list-create')).toBeNull();
  });
  describe('gestion', () => {
    function clickAction(label: string): void {
      const button = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.campaign-action'))
                          .find((candidate) => candidate.textContent?.trim() === label);
      button!.click();
      fixture.detectChanges();
    }

    it('should not offer any action to a reader', () => {
      signInAs(['campaigns:read']);
      render();

      expect(compiled.querySelector('.campaign-detail-actions')).toBeNull();
    });

    it('should open the form prefilled with the selected campaign', () => {
      render();
      clickAction('Modifier');

      const modal = compiled.querySelector('app-campaign-form-modal')!;
      expect(modal.querySelector('h2')?.textContent).toContain('Modifier la campagne');
      expect((modal.querySelector('#campaign-name') as HTMLInputElement).value).toBe('Marché de Noël');
    });

    /** Un jour inchangé garde son heure : celle d'une clôture, par exemple. */
    it('should PUT the edited campaign, keeping the time of an unchanged day', () => {
      const success = vi.spyOn(notifications, 'success');
      render([campaign({ endDate: '2026-12-24T17:42:00' })]);
      clickAction('Modifier');

      const modal = compiled.querySelector('app-campaign-form-modal')!;
      const name = modal.querySelector('#campaign-name') as HTMLInputElement;
      name.value = 'Marché de Noël 2026';
      name.dispatchEvent(new Event('input'));
      const start = modal.querySelector('#campaign-start') as HTMLInputElement;
      start.value = '2026-11-28';
      start.dispatchEvent(new Event('input'));
      modal.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      const request = httpTesting.expectOne('/api/campaigns/1');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({
        name: 'Marché de Noël 2026',
        description: 'Toutes les annonces du marché',
        startDate: '2026-11-28T00:00:00',
        endDate: '2026-12-24T17:42:00',
      });

      request.flush(campaign({ name: 'Marché de Noël 2026' }));
      fixture.detectChanges();

      expect(compiled.querySelector('app-campaign-form-modal')).toBeNull();
      expect(compiled.querySelector('app-campaign-detail h2')?.textContent?.trim()).toBe('Marché de Noël 2026');
      expect(success).toHaveBeenCalledWith('Campagne modifiée.');
    });

    it('should keep the form open and warn when the update fails', () => {
      const error = vi.spyOn(notifications, 'error');
      render();
      clickAction('Modifier');
      compiled.querySelector('app-campaign-form-modal form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      httpTesting.expectOne('/api/campaigns/1').flush('boom', { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();

      expect(compiled.querySelector('app-campaign-form-modal')).not.toBeNull();
      expect(error).toHaveBeenCalledWith('La campagne n’a pas pu être modifiée.');
    });

    it('should close a running campaign once confirmed and show its new status', () => {
      render([campaign({ status: 'ACTIVE' })]);
      clickAction('Clôturer');
      clickAction('Confirmer la clôture');

      const request = httpTesting.expectOne('/api/campaigns/1/closure');
      expect(request.request.method).toBe('POST');
      request.flush(campaign({ status: 'COMPLETED' }));
      fixture.detectChanges();

      expect(compiled.querySelector('app-campaign-detail .campaign-badge')?.textContent?.trim()).toBe('Terminée');
    });

    it('should cancel a campaign once confirmed', () => {
      const success = vi.spyOn(notifications, 'success');
      render();
      clickAction('Annuler la campagne');
      clickAction('Confirmer l’annulation');

      httpTesting.expectOne('/api/campaigns/1/cancellation').flush(campaign({ status: 'CANCELED' }));
      fixture.detectChanges();

      expect(compiled.querySelector('app-campaign-detail .campaign-badge')?.textContent?.trim()).toBe('Annulée');
      expect(success).toHaveBeenCalledWith('Campagne annulée.');
    });

    it('should remove a deleted campaign and select the next one', () => {
      const success = vi.spyOn(notifications, 'success');
      render([campaign(), campaign({ id: 2, name: 'Budget participatif' })]);
      clickAction('Supprimer');
      clickAction('Confirmer la suppression');

      const request = httpTesting.expectOne('/api/campaigns/1');
      expect(request.request.method).toBe('DELETE');
      request.flush(null, { status: 204, statusText: 'No Content' });
      fixture.detectChanges();

      expect(compiled.querySelectorAll('.campaign-list-item').length).toBe(1);
      expect(compiled.querySelector('app-campaign-detail h2')?.textContent?.trim()).toBe('Budget participatif');
      expect(success).toHaveBeenCalledWith('Campagne supprimée.');
    });

    it('should keep the campaign and warn when an action is refused', () => {
      const error = vi.spyOn(notifications, 'error');
      render();
      clickAction('Annuler la campagne');
      clickAction('Confirmer l’annulation');

      httpTesting.expectOne('/api/campaigns/1/cancellation')
                 .flush({ message: 'La campagne 1 est terminée' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();

      expect(compiled.querySelector('app-campaign-detail .campaign-badge')?.textContent?.trim()).toBe('Programmée');
      expect(error).toHaveBeenCalledWith('La campagne n’a pas pu être annulée.');
    });
  });
  describe('publications de la campagne', () => {
    function publication(id: number, content: string, campaignRef: Publication['campaign'] = null): Publication {
      return { id, content, status: 'VERIFIED', medias: [], campaign: campaignRef };
    }

    const samedi = publication(8, 'Rendez-vous samedi', { id: 1, name: 'Marché de Noël' });

    function clickButton(label: string): void {
      const button = Array.from(compiled.querySelectorAll<HTMLButtonElement>('button'))
                          .find((candidate) => candidate.textContent?.replace(/\s+/g, ' ').trim().startsWith(label));
      button!.click();
      fixture.detectChanges();
    }

    it('should load the diffusions of the selected campaign and show them', () => {
      render([campaign({ publications: [samedi] })]);
      fixture.detectChanges();

      const request = httpTesting.expectOne('/api/campaigns/1/occurrences');
      request.flush([{
        id: 5,
        scheduledAt: '2026-12-05T18:30:00',
        pinned: false,
        status: 'SCHEDULED',
        publication: samedi,
        deliveries: [{ id: 50, channel: 'FACEBOOK', status: 'PENDING' }],
      } satisfies Occurrence]);
      fixture.detectChanges();

      expect(compiled.querySelector('.campaign-detail-publication-planning')?.textContent)
        .toContain('Prochaine diffusion le sam. 5 déc. à 18:30');
    });

    it('should reload the diffusions when another campaign is selected', () => {
      render([campaign(), campaign({ id: 2, name: 'Budget participatif' })]);
      flushPlanning();

      compiled.querySelectorAll<HTMLElement>('.campaign-list-item')[1].click();
      fixture.detectChanges();

      httpTesting.expectOne('/api/campaigns/2/occurrences').flush([]);
    });

    it('should warn when the diffusions cannot be loaded', () => {
      render();
      fixture.detectChanges();

      httpTesting.expectOne('/api/campaigns/1/occurrences').flush('boom', { status: 500, statusText: 'Error' });
      fixture.detectChanges();

      expect(compiled.querySelector('.campaign-detail-warning')).not.toBeNull();
    });

    it('should add the checked publications, moving one from another campaign', () => {
      const success = vi.spyOn(notifications, 'success');
      const ailleurs = publication(9, 'Concert', { id: 2, name: 'Budget participatif' });
      render([campaign(), campaign({ id: 2, name: 'Budget participatif', publications: [ailleurs] })]);
      flushPlanning();

      clickButton('Ajouter des publications');
      httpTesting.expectOne('/api/publications').flush([ailleurs, publication(10, 'Sans campagne')]);
      fixture.detectChanges();

      compiled.querySelectorAll<HTMLInputElement>('app-add-publications-modal input[type="checkbox"]')
              .forEach((box) => box.click());
      fixture.detectChanges();
      clickButton('Ajouter (2)');

      const requests = httpTesting.match((request) => request.url.endsWith('/campaign'));
      expect(requests.map((request) => [request.request.url, request.request.body]))
        .toEqual([['/api/publications/9/campaign', { campaignId: 1 }], ['/api/publications/10/campaign', { campaignId: 1 }]]);
      requests[0].flush({ ...ailleurs, campaign: { id: 1, name: 'Marché de Noël' } });
      requests[1].flush(publication(10, 'Sans campagne', { id: 1, name: 'Marché de Noël' }));
      flushPlanning();

      expect(compiled.querySelector('app-add-publications-modal')).toBeNull();
      expect(compiled.querySelectorAll('app-campaign-detail .campaign-detail-publication').length).toBe(2);
      expect(compiled.querySelectorAll('.campaign-list-item')[1].textContent).toContain('0 publication');
      expect(success).toHaveBeenCalledWith('2 publications ajoutées à la campagne.');
    });

    it('should detach a publication from the campaign', () => {
      const success = vi.spyOn(notifications, 'success');
      render([campaign({ publications: [samedi] })]);
      flushPlanning();

      clickButton('Retirer');

      const request = httpTesting.expectOne('/api/publications/8/campaign');
      expect(request.request.body).toEqual({ campaignId: null });
      request.flush({ ...samedi, campaign: null });
      flushPlanning();

      expect(compiled.querySelector('.campaign-detail-placeholder')).not.toBeNull();
      expect(success).toHaveBeenCalledWith('Publication retirée de la campagne.');
    });

    it('should schedule a publication from the campaign and reload its diffusions', () => {
      const success = vi.spyOn(notifications, 'success');
      render([campaign({ publications: [samedi] })]);
      flushPlanning();

      clickButton('Programmer');
      expect(compiled.querySelector('app-schedule-occurrence-modal')).not.toBeNull();

      fixture.debugElement.query((el) => el.name === 'app-schedule-occurrence-modal')
             .componentInstance.submitted.emit({ slots: [{ date: '2026-12-05', time: '18:30' }], channels: ['FACEBOOK'] });
      fixture.detectChanges();

      const request = httpTesting.expectOne('/api/occurrences/batch');
      expect(request.request.body).toEqual({
        publicationId: 8,
        channels: ['FACEBOOK'],
        slots: [{ scheduledAt: '2026-12-05T18:30:00' }],
      });
      request.flush([{
        id: 5, scheduledAt: '2026-12-05T18:30:00', pinned: true, status: 'SCHEDULED', publication: samedi,
        deliveries: [{ id: 50, channel: 'FACEBOOK', status: 'PENDING' }],
      } satisfies Occurrence]);
      fixture.detectChanges();

      expect(compiled.querySelector('app-schedule-occurrence-modal')).toBeNull();
      expect(success).toHaveBeenCalledWith('Diffusion programmée le samedi 5 décembre à 18:30.');
      httpTesting.expectOne('/api/campaigns/1/occurrences').flush([]);
    });

    it('should offer neither scheduling nor adding to a campaign reader', () => {
      signInAs(['campaigns:read']);
      render([campaign({ publications: [samedi] })]);
      flushPlanning();

      expect(compiled.textContent).not.toContain('Ajouter des publications');
      expect(compiled.querySelector('.campaign-detail-publication-actions')).toBeNull();
    });
  });
});
