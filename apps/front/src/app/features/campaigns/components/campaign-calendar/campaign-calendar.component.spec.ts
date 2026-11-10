import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { Campaign } from '../../campaign.model';
import { CampaignCalendarComponent, initialMonth } from './campaign-calendar.component';

registerLocaleData(localeFr, 'fr-FR');

const NOW = new Date(2026, 10, 10, 12, 0);

function occurrence(id: number, scheduledAt: string): Occurrence {
  return {
    id,
    scheduledAt,
    pinned: false,
    status: 'SCHEDULED',
    publication: { id, content: `Publication ${ id }`, status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [{ id: id * 10, channel: 'FACEBOOK', status: 'PENDING' }],
  };
}

function campaign(overrides: Partial<Campaign> = {}): Campaign {
  return {
    id: 1,
    name: 'Marché de Noël',
    description: '',
    startDate: '2026-12-01T00:00:00',
    endDate: '2026-12-24T00:00:00',
    status: 'SCHEDULED',
    publications: [],
    ...overrides,
  };
}

describe('initialMonth', () => {
  it('should open on the start of a campaign still to come', () => {
    expect(initialMonth({ start: '2026-12-01', end: null }, '2026-11-10')).toEqual({ year: 2026, month: 11 });
  });

  it('should open on the end of a finished campaign', () => {
    expect(initialMonth({ start: '2026-03-01', end: '2026-06-30' }, '2026-11-10')).toEqual({ year: 2026, month: 5 });
  });

  it('should open on today during the campaign, or without dates', () => {
    expect(initialMonth({ start: '2026-11-01', end: '2026-12-31' }, '2026-11-10')).toEqual({ year: 2026, month: 10 });
    expect(initialMonth({ start: null, end: null }, '2026-11-10')).toEqual({ year: 2026, month: 10 });
  });
});

describe('CampaignCalendarComponent', () => {
  let fixture: ComponentFixture<CampaignCalendarComponent>;
  let compiled: HTMLElement;

  function render(value: Campaign, occurrences: Occurrence[] = []): void {
    fixture.componentRef.setInput('campaign', value);
    fixture.componentRef.setInput('occurrences', occurrences);
    fixture.componentRef.setInput('now', NOW);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function click(label: string): void {
    Array.from(compiled.querySelectorAll<HTMLButtonElement>('button'))
         .find((button) => button.textContent?.trim() === label || button.getAttribute('aria-label') === label)!
         .click();
    fixture.detectChanges();
  }

  function title(): string {
    return compiled.querySelector('.campaign-calendar-title')?.textContent?.trim() ?? '';
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignCalendarComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(CampaignCalendarComponent);
  });

  it('should open on the month the campaign starts', () => {
    render(campaign());

    expect(title()).toBe('Décembre 2026');
  });

  it('should place the diffusions of the campaign on their day', () => {
    render(campaign(), [occurrence(5, '2026-12-05T18:30:00')]);

    expect(compiled.querySelectorAll('app-calendar-occurrence-chip').length).toBe(1);
  });

  it('should hatch the days outside the period', () => {
    render(campaign());

    // 1er au 24 décembre : du 25 au 31, sept jours hors période.
    expect(compiled.querySelectorAll('.month-cell-off-period').length).toBe(7);
  });

  it('should navigate between months, back to the start and to today', () => {
    render(campaign());

    click('Mois suivant');
    expect(title()).toBe('Janvier 2027');
    click('Aujourd’hui');
    expect(title()).toBe('Novembre 2026');
    click('Début');
    expect(title()).toBe('Décembre 2026');
    click('Mois précédent');
    expect(title()).toBe('Novembre 2026');
  });

  it('should not offer "Début" to a campaign without a start date', () => {
    render(campaign({ startDate: null, endDate: null }));

    expect(Array.from(compiled.querySelectorAll('button')).some((button) => button.textContent?.trim() === 'Début'))
      .toBe(false);
  });

  it('should list the diffusions outside the period', () => {
    render(campaign(), [
      occurrence(5, '2026-12-05T18:30:00'),
      occurrence(6, '2026-11-28T12:00:00'),
      occurrence(7, '2027-01-03T12:00:00'),
    ]);

    const warning = compiled.querySelector('.campaign-calendar-warning');
    expect(warning?.textContent).toContain('2 diffusions tombent hors de la période');
    expect(warning?.querySelectorAll('li').length).toBe(2);
    expect(warning?.textContent).toContain('Publication 6');
  });

  it('should not warn when every diffusion is within the period', () => {
    render(campaign({ endDate: null }), [occurrence(5, '2027-02-05T18:30:00')]);

    expect(compiled.querySelector('.campaign-calendar-warning')).toBeNull();
  });

  it('should open a read-only day panel on click', () => {
    render(campaign(), [occurrence(5, '2026-12-05T18:30:00')]);

    compiled.querySelector<HTMLButtonElement>('[aria-label="Voir les diffusions du 5 (1)"]')!.click();
    fixture.detectChanges();

    expect(compiled.querySelector('app-calendar-day-panel')).not.toBeNull();
  });

  it('should send to the general calendar for changes', () => {
    render(campaign());

    expect(compiled.querySelector('.campaign-calendar-hint a')?.getAttribute('href')).toBe('/calendrier');
  });
});
