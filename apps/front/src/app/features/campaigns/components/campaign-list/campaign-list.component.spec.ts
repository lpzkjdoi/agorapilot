import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Campaign } from '../../campaign.model';
import { CampaignListComponent } from './campaign-list.component';

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

describe('CampaignListComponent', () => {
  let fixture: ComponentFixture<CampaignListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CampaignListComponent);
  });

  function render(campaigns: Campaign[], selectedId: number | null = null): HTMLElement {
    fixture.componentRef.setInput('campaigns', campaigns);
    fixture.componentRef.setInput('selectedId', selectedId);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('should render a campaign with its status, date and publication count', () => {
    const compiled = render([
      campaign({ publications: [{ id: 7, content: 'Rendez-vous samedi', status: 'VERIFIED', medias: [], campaign: null }] }),
    ]);

    const item = compiled.querySelector('.campaign-list-item') as HTMLElement;
    expect(item.querySelector('.campaign-list-item-name')?.textContent?.trim()).toBe('Marché de Noël');
    expect(item.querySelector('.campaign-badge')?.textContent?.trim()).toBe('Programmée');
    expect(item.querySelector('.campaign-list-item-date')?.textContent).toContain('déc.');
    expect(item.querySelector('.campaign-list-item-count')?.textContent?.trim()).toBe('1 publication');
  });

  it('should pluralise the publication count', () => {
    const publications = [
      { id: 7, content: 'Un', status: 'VERIFIED' as const, medias: [], campaign: null },
      { id: 8, content: 'Deux', status: 'DRAFT' as const, medias: [], campaign: null },
    ];

    const compiled = render([campaign({ publications })]);

    expect(compiled.querySelector('.campaign-list-item-count')?.textContent?.trim())
      .toBe('2 publications');
  });

  it('should omit the date line when the campaign has no start date', () => {
    const compiled = render([campaign({ startDate: null })]);

    expect(compiled.querySelector('.campaign-list-item-date')).toBeNull();
  });

  it('should mark the selected campaign', () => {
    const compiled = render([campaign({ id: 1 }), campaign({ id: 2, name: 'Budget' })], 2);

    const items = compiled.querySelectorAll('.campaign-list-item');
    expect(items[0].classList.contains('selected')).toBe(false);
    expect(items[1].classList.contains('selected')).toBe(true);
    expect(items[1].getAttribute('aria-current')).toBe('true');
  });

  it('should emit the campaign clicked', () => {
    const selected = vi.fn();
    const first = campaign({ id: 1 });
    fixture.componentRef.setInput('campaigns', [first]);
    fixture.detectChanges();
    fixture.componentInstance.selected.subscribe(selected);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.campaign-list-item')!
                                         .click();

    expect(selected).toHaveBeenCalledWith(first);
  });

  it('should emit a creation request when the + button is clicked', () => {
    const createRequested = vi.fn();
    render([]);
    fixture.componentInstance.createRequested.subscribe(createRequested);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.campaign-list-create')!
                                         .click();

    expect(createRequested).toHaveBeenCalled();
  });

  it('should filter the list on the search input', () => {
    const compiled = render([campaign({ id: 1 }), campaign({ id: 2, name: 'Budget participatif' })]);
    const search = compiled.querySelector('input[type="search"]') as HTMLInputElement;

    search.value = 'budget';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const items = compiled.querySelectorAll('.campaign-list-item');
    expect(items.length).toBe(1);
    expect(items[0].textContent).toContain('Budget participatif');
  });

  it('should tell the search matched nothing', () => {
    const compiled = render([campaign()]);
    const search = compiled.querySelector('input[type="search"]') as HTMLInputElement;

    search.value = 'zzz';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(compiled.querySelector('.campaign-list-placeholder')?.textContent)
      .toContain('Aucune campagne ne correspond');
  });

  it('should tell there is no campaign at all', () => {
    const compiled = render([]);

    expect(compiled.querySelector('.campaign-list-placeholder')?.textContent)
      .toContain('Aucune campagne pour le moment');
  });
});
