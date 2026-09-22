import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Publication } from '../../../publications/publication.model';
import { Campaign } from '../../campaign.model';
import { CampaignDetailComponent } from './campaign-detail.component';

registerLocaleData(localeFr, 'fr-FR');

function publication(id: number, content: string, status: Publication['status']): Publication {
  return { id, content, status, medias: [] };
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

  it('should count the publications by status', () => {
    const compiled = render(campaign({
      publications: [
        publication(1, 'Un', 'VERIFIED'),
        publication(2, 'Deux', 'VERIFIED'),
        publication(3, 'Trois', 'DRAFT'),
      ],
    }));

    const values = Array.from(compiled.querySelectorAll('.campaign-detail-stat-value'))
                        .map((value) => value.textContent?.trim());

    expect(values).toEqual(['3', '2', '1']);
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
});
