import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Campaign } from '../../../campaigns/campaign.model';
import {
  PublicationFiltersComponent,
  PublicationStats,
} from './publication-filters.component';

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

const stats: PublicationStats = { total: 6, verified: 3, drafts: 3, inCampaigns: 4 };

describe('PublicationFiltersComponent', () => {
  let fixture: ComponentFixture<PublicationFiltersComponent>;
  let compiled: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicationFiltersComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PublicationFiltersComponent);
    fixture.componentRef.setInput('campaigns', campaigns);
    fixture.componentRef.setInput('stats', stats);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the counters of the stats bar', () => {
    const items = Array.from(compiled.querySelectorAll('.filters-stats li')).map((item) =>
      item.textContent?.replace(/\s+/g, ' ').trim(),
    );

    expect(items).toEqual([
      'Total : 6',
      'Vérifiées : 3',
      'Brouillons : 3',
      'Dans campagnes : 4',
    ]);
  });

  it('should list the campaigns after the two generic options', () => {
    const select = compiled.querySelector('#publications-campaign') as HTMLSelectElement;
    const options = Array.from(select.options).map((option) => option.textContent?.trim());

    expect(options).toEqual(['Toutes les campagnes', 'Sans campagne', 'Parcs & Loisirs']);
  });

  it('should emit the search term on input', () => {
    const emitted = vi.fn();
    fixture.componentInstance.searchChanged.subscribe(emitted);

    const input = compiled.querySelector('#publications-search') as HTMLInputElement;
    input.value = 'marché';
    input.dispatchEvent(new Event('input'));

    expect(emitted).toHaveBeenCalledWith('marché');
  });

  it('should emit the selected status', () => {
    const emitted = vi.fn();
    fixture.componentInstance.statusChanged.subscribe(emitted);

    const select = compiled.querySelector('#publications-status') as HTMLSelectElement;
    select.value = 'DRAFT';
    select.dispatchEvent(new Event('change'));

    expect(emitted).toHaveBeenCalledWith('DRAFT');
  });

  it('should emit `ALL`/`NONE` as-is and a campaign id as a number', () => {
    const emitted = vi.fn();
    fixture.componentInstance.campaignChanged.subscribe(emitted);

    const select = compiled.querySelector('#publications-campaign') as HTMLSelectElement;

    select.value = 'NONE';
    select.dispatchEvent(new Event('change'));
    expect(emitted).toHaveBeenLastCalledWith('NONE');

    select.value = '7';
    select.dispatchEvent(new Event('change'));
    expect(emitted).toHaveBeenLastCalledWith(7);

    select.value = 'ALL';
    select.dispatchEvent(new Event('change'));
    expect(emitted).toHaveBeenLastCalledWith('ALL');
  });

  it('should keep every control labelled for screen readers', () => {
    const labels = Array.from(compiled.querySelectorAll('label')).map((label) => label.htmlFor);

    expect(labels).toEqual([
      'publications-search',
      'publications-status',
      'publications-campaign',
    ]);
  });
});
