import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Occurrence, OccurrenceStatus } from '../../../occurrences/occurrence.model';
import { Publication } from '../../../publications/publication.model';
import { Campaign } from '../../campaign.model';
import { CampaignDetailComponent } from './campaign-detail.component';

registerLocaleData(localeFr, 'fr-FR');

function publication(id: number, content: string, status: Publication['status']): Publication {
  return { id, content, status, medias: [], campaign: null };
}

function occurrence(id: number, of: Publication, scheduledAt: string, status: OccurrenceStatus): Occurrence {
  return {
    id,
    scheduledAt,
    pinned: false,
    status,
    publication: of,
    deliveries: [{
      id,
      channel: 'FACEBOOK',
      status: status === 'SCHEDULED' ? 'PENDING' : status,
      errorMessage: status === 'FAILED' ? 'Jeton expiré' : null,
    }],
  };
}

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

describe('CampaignDetailComponent', () => {
  let fixture: ComponentFixture<CampaignDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignDetailComponent],
      // Le calendrier de campagne renvoie au Calendrier général par un lien.
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(CampaignDetailComponent);
  });

  function render(value: Campaign): HTMLElement {
    fixture.componentRef.setInput('campaign', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('should render the name, status and description', () => {
    const compiled = render(campaign());

    expect(compiled.querySelector('h2')?.textContent?.trim()).toBe('Marché de Noël');
    expect(compiled.querySelector('.campaign-badge')?.textContent?.trim()).toBe('Programmée');
    expect(compiled.querySelector('.campaign-detail-description')?.textContent?.trim())
      .toBe('Toutes les annonces du marché');
  });

  it('should render the full period when both dates are set', () => {
    const period = render(campaign()).querySelector('.campaign-detail-period')?.textContent ?? '';

    expect(period).toContain('Du 1 décembre 2026');
    expect(period).toContain('au 24 décembre 2026');
  });

  it('should render an open-ended period when only the start date is set', () => {
    const period = render(campaign({ endDate: null }))
      .querySelector('.campaign-detail-period')?.textContent ?? '';

    expect(period).toContain('À partir du 1 décembre 2026');
  });

  it('should omit the period when the campaign has no date', () => {
    const compiled = render(campaign({ startDate: null, endDate: null }));

    expect(compiled.querySelector('.campaign-detail-period')).toBeNull();
  });

  it('should only count the publications while the diffusions load', () => {
    const compiled = render(campaign({
      publications: [publication(1, 'Un', 'VERIFIED'), publication(2, 'Deux', 'DRAFT')],
    }));

    const values = Array.from(compiled.querySelectorAll('.campaign-detail-stat-value'))
                        .map((value) => value.textContent?.trim());

    expect(values).toEqual(['2']);
    expect(compiled.querySelector('.campaign-detail-publication-planning')).toBeNull();
  });

  describe('planning', () => {
    const un = publication(1, 'Un', 'VERIFIED');
    const deux = publication(2, 'Deux', 'VERIFIED');
    const trois = publication(3, 'Trois', 'DRAFT');

    function renderPlanned(): HTMLElement {
      fixture.componentRef.setInput('occurrences', [
        occurrence(10, un, '2026-12-05T18:30:00', 'SCHEDULED'),
        occurrence(11, un, '2026-12-12T18:30:00', 'SCHEDULED'),
        occurrence(12, un, '2026-11-01T12:00:00', 'PUBLISHED'),
        occurrence(13, deux, '2026-11-03T12:00:00', 'FAILED'),
      ]);
      return render(campaign({ publications: [un, deux, trois] }));
    }

    function planningOf(compiled: HTMLElement, index: number): string {
      return compiled.querySelectorAll('.campaign-detail-publication')[index]
                     .querySelector('.campaign-detail-publication-planning')?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
    }

    it('should count the diffusions by status', () => {
      const values = Array.from(renderPlanned().querySelectorAll('.campaign-detail-stat-value'))
                          .map((value) => value.textContent?.trim());

      expect(values).toEqual(['3', '2', '1', '1']);
    });

    it('should show the next diffusion, its channel and how many follow', () => {
      const text = planningOf(renderPlanned(), 0);

      expect(text).toContain('Prochaine diffusion le sam. 5 déc. à 18:30');
      expect(text).toContain('Facebook');
      expect(text).toContain('+ 1 autre(s)');
      expect(text).toContain('Publiée le 1 nov.');
    });

    it('should flag the last failed diffusion with its reason', () => {
      const compiled = renderPlanned();

      expect(planningOf(compiled, 1)).toContain('Aucune diffusion programmée');
      const failed = compiled.querySelectorAll('.campaign-detail-publication')[1].querySelector('.planning-failed');
      expect(failed?.textContent).toContain('Échec le 3 nov.');
      expect(failed?.getAttribute('title')).toBe('Jeton expiré');
    });

    it('should warn when the planning could not be loaded', () => {
      fixture.componentRef.setInput('planningUnavailable', true);

      expect(render(campaign()).querySelector('.campaign-detail-warning')?.textContent)
        .toContain('n’a pas pu être chargée');
    });

    it('should offer to schedule verified publications only', () => {
      fixture.componentRef.setInput('canSchedule', true);
      const schedule = vi.fn();
      fixture.componentInstance.schedule.subscribe(schedule);
      const compiled = renderPlanned();

      const rows = compiled.querySelectorAll('.campaign-detail-publication');
      expect(rows[2].querySelector('.campaign-detail-publication-actions')).toBeNull();
      (rows[0].querySelector('.campaign-action') as HTMLButtonElement).click();

      expect(schedule).toHaveBeenCalledWith(un);
    });

    it('should offer to add and detach publications with the publication write permission', () => {
      fixture.componentRef.setInput('canAssign', true);
      const add = vi.fn();
      const detach = vi.fn();
      fixture.componentInstance.addPublications.subscribe(add);
      fixture.componentInstance.detach.subscribe(detach);
      const compiled = renderPlanned();

      const actions = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.campaign-action'));
      actions.find((button) => button.textContent?.trim() === 'Ajouter des publications')!.click();
      actions.filter((button) => button.textContent?.trim() === 'Retirer')[2].click();

      expect(add).toHaveBeenCalledTimes(1);
      expect(detach).toHaveBeenCalledWith(trois);
    });

    it('should offer neither without the permissions', () => {
      const compiled = renderPlanned();

      expect(compiled.querySelector('.campaign-detail-publication-actions')).toBeNull();
      expect(compiled.textContent).not.toContain('Ajouter des publications');
    });
  });

  it('should list the attached publications with their status', () => {
    const compiled = render(campaign({
      publications: [publication(1, 'Rendez-vous samedi', 'DRAFT')],
    }));

    const item = compiled.querySelector('.campaign-detail-publication') as HTMLElement;
    expect(item.querySelector('.campaign-detail-publication-content')?.textContent?.trim())
      .toBe('Rendez-vous samedi');
    expect(item.querySelector('.campaign-badge')?.textContent?.trim()).toBe('Brouillon');
  });

  it('should tell the campaign has no publication', () => {
    const compiled = render(campaign());

    expect(compiled.querySelector('.campaign-detail-placeholder')?.textContent)
      .toContain('Aucune publication rattachée');
  });
  describe('management actions', () => {
    function renderWritable(value: Campaign): HTMLElement {
      fixture.componentRef.setInput('canWrite', true);
      return render(value);
    }

    function buttons(compiled: HTMLElement): string[] {
      return Array.from(compiled.querySelectorAll('.campaign-action'))
                  .map((button) => button.textContent?.trim() ?? '');
    }

    function click(compiled: HTMLElement, label: string): void {
      const button = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.campaign-action'))
                          .find((candidate) => candidate.textContent?.trim() === label);
      button!.click();
      fixture.detectChanges();
    }

    it('should stay read-only without the write permission', () => {
      expect(render(campaign()).querySelector('.campaign-detail-actions')).toBeNull();
    });

    it('should offer close only to a running campaign', () => {
      expect(buttons(renderWritable(campaign({ status: 'ACTIVE' }))))
        .toEqual(['Modifier', 'Clôturer', 'Annuler la campagne', 'Supprimer']);
    });

    it('should offer cancel but not close to a scheduled campaign', () => {
      expect(buttons(renderWritable(campaign({ status: 'SCHEDULED' }))))
        .toEqual(['Modifier', 'Annuler la campagne', 'Supprimer']);
    });

    it('should only offer edit and delete to a finished or canceled campaign', () => {
      expect(buttons(renderWritable(campaign({ status: 'COMPLETED' })))).toEqual(['Modifier', 'Supprimer']);
      expect(buttons(renderWritable(campaign({ id: 2, status: 'CANCELED' })))).toEqual(['Modifier', 'Supprimer']);
    });

    it('should emit edit right away', () => {
      const edit = vi.fn();
      fixture.componentInstance.edit.subscribe(edit);

      click(renderWritable(campaign()), 'Modifier');

      expect(edit).toHaveBeenCalledTimes(1);
    });

    it('should ask for confirmation before deleting', () => {
      const confirmed = vi.fn();
      fixture.componentInstance.actionConfirmed.subscribe(confirmed);
      const compiled = renderWritable(campaign());

      click(compiled, 'Supprimer');

      expect(confirmed).not.toHaveBeenCalled();
      expect(compiled.querySelector('.campaign-detail-confirm')?.textContent)
        .toContain('Ses publications sont conservées');

      click(compiled, 'Confirmer la suppression');

      expect(confirmed).toHaveBeenCalledWith('archive');
      expect(compiled.querySelector('.campaign-detail-confirm')).toBeNull();
    });

    it('should emit close and cancel once confirmed', () => {
      const confirmed = vi.fn();
      fixture.componentInstance.actionConfirmed.subscribe(confirmed);
      const compiled = renderWritable(campaign({ status: 'ACTIVE' }));

      click(compiled, 'Clôturer');
      click(compiled, 'Confirmer la clôture');
      click(compiled, 'Annuler la campagne');
      expect(compiled.querySelector('.campaign-detail-confirm')?.textContent)
        .toContain('seront déprogrammées');
      click(compiled, 'Confirmer l’annulation');

      expect(confirmed.mock.calls).toEqual([['close'], ['cancel']]);
    });

    it('should drop the pending confirmation on "Retour"', () => {
      const confirmed = vi.fn();
      fixture.componentInstance.actionConfirmed.subscribe(confirmed);
      const compiled = renderWritable(campaign());

      click(compiled, 'Supprimer');
      click(compiled, 'Retour');

      expect(compiled.querySelector('.campaign-detail-confirm')).toBeNull();
      expect(confirmed).not.toHaveBeenCalled();
    });

    it('should drop the pending confirmation when another campaign is selected', () => {
      const compiled = renderWritable(campaign());
      click(compiled, 'Supprimer');

      render(campaign({ id: 2, name: 'Autre' }));

      expect(compiled.querySelector('.campaign-detail-confirm')).toBeNull();
    });

    it('should disable the actions while one is in flight', () => {
      fixture.componentRef.setInput('busy', true);
      const compiled = renderWritable(campaign());

      const disabled = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.campaign-action'))
                            .every((button) => button.disabled);
      expect(disabled).toBe(true);
    });
  });
  describe('tabs', () => {
    function tab(compiled: HTMLElement, label: string): HTMLButtonElement {
      return Array.from(compiled.querySelectorAll<HTMLButtonElement>('.campaign-detail-tab'))
                  .find((button) => button.textContent?.trim().startsWith(label))!;
    }

    it('should open on the publications, counted in their tab', () => {
      const compiled = render(campaign({ publications: [publication(1, 'Un', 'VERIFIED')] }));

      expect(tab(compiled, 'Publications').textContent?.trim()).toBe('Publications (1)');
      expect(tab(compiled, 'Publications').getAttribute('aria-selected')).toBe('true');
      expect(compiled.querySelector('.campaign-detail-publications')).not.toBeNull();
      expect(compiled.querySelector('app-campaign-calendar')).toBeNull();
    });

    it('should show the campaign calendar once its diffusions are loaded', () => {
      const compiled = render(campaign());

      tab(compiled, 'Calendrier').click();
      fixture.detectChanges();
      expect(compiled.querySelector('app-campaign-calendar')).toBeNull();
      expect(compiled.textContent).toContain('Chargement des diffusions');

      fixture.componentRef.setInput('occurrences', []);
      fixture.detectChanges();

      expect(compiled.querySelector('app-campaign-calendar')).not.toBeNull();
      expect(compiled.querySelector('.campaign-detail-publications, .campaign-detail-placeholder')).toBeNull();
      expect(tab(compiled, 'Calendrier').getAttribute('aria-selected')).toBe('true');
    });

    it('should keep the tab when another campaign is selected', () => {
      fixture.componentRef.setInput('occurrences', []);
      const compiled = render(campaign());
      tab(compiled, 'Calendrier').click();
      fixture.detectChanges();

      render(campaign({ id: 2, name: 'Autre' }));

      expect(compiled.querySelector('app-campaign-calendar')).not.toBeNull();
    });
  });
});
