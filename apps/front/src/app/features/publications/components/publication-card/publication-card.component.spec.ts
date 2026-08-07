import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Publication } from '../../publication.model';
import { PublicationCardComponent } from './publication-card.component';

registerLocaleData(localeFr, 'fr-FR');

const draft: Publication = {
  id: 1,
  content: 'Fermeture exceptionnelle de la mairie',
  status: 'DRAFT',
};

async function render(publication: Publication): Promise<ComponentFixture<PublicationCardComponent>> {
  const fixture = TestBed.createComponent(PublicationCardComponent);
  fixture.componentRef.setInput('publication', publication);
  fixture.detectChanges();
  return fixture;
}

describe('PublicationCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicationCardComponent],
    }).compileComponents();
  });

  it('should create', async () => {
    const fixture = await render(draft);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the content and the `Brouillon` badge of a draft', async () => {
    const fixture = await render(draft);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.content')?.textContent).toContain(
      'Fermeture exceptionnelle de la mairie',
    );
    const badge = compiled.querySelector('.status');
    expect(badge?.textContent?.trim()).toBe('Brouillon');
    expect(badge?.classList.contains('status-draft')).toBe(true);
  });

  it('should render the `Vérifié` badge of a verified publication', async () => {
    const fixture = await render({ ...draft, status: 'VERIFIED' });
    const badge = (fixture.nativeElement as HTMLElement).querySelector('.status');

    expect(badge?.textContent?.trim()).toBe('Vérifié');
    expect(badge?.classList.contains('status-verified')).toBe(true);
  });

  it('should fall back to `Sans campagne` when no campaign is attached', async () => {
    const fixture = await render(draft);
    const badge = (fixture.nativeElement as HTMLElement).querySelector('.campaign');

    expect(badge?.textContent).toContain('Sans campagne');
    expect(badge?.classList.contains('campaign-none')).toBe(true);
  });

  it('should render the campaign name and offer to change it when one is attached', async () => {
    const fixture = await render({ ...draft, campaign: { id: 7, name: 'Parcs & Loisirs' } });
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.campaign')?.textContent).toContain(
      'Parcs & Loisirs',
    );
    expect(compiled.querySelector('.action-campaign')?.textContent).toContain(
      'Changer de campagne',
    );
  });

  it('should not render the date line while the API does not expose `createdAt`', async () => {
    const fixture = await render(draft);
    expect((fixture.nativeElement as HTMLElement).querySelector('.date')).toBeNull();
  });

  it('should format `createdAt` in French when the API exposes it', async () => {
    const fixture = await render({ ...draft, createdAt: '2026-03-20T10:30:00' });
    const date = (fixture.nativeElement as HTMLElement).querySelector('.date');

    expect(date?.textContent).toContain('20 mars 2026');
    expect(date?.textContent).toContain('10:30');
  });

  it('should emit the publication on each of the three actions', async () => {
    const fixture = await render(draft);
    const component = fixture.componentInstance;
    const compiled = fixture.nativeElement as HTMLElement;

    const xlsx = vi.fn();
    const facebook = vi.fn();
    const campaign = vi.fn();
    component.generateXlsx.subscribe(xlsx);
    component.publishOnFacebook.subscribe(facebook);
    component.assignCampaign.subscribe(campaign);

    compiled.querySelector<HTMLButtonElement>('.action-xlsx')?.click();
    compiled.querySelector<HTMLButtonElement>('.action-facebook')?.click();
    compiled.querySelector<HTMLButtonElement>('.action-campaign')?.click();

    expect(xlsx).toHaveBeenCalledWith(draft);
    expect(facebook).toHaveBeenCalledWith(draft);
    expect(campaign).toHaveBeenCalledWith(draft);
  });

  it('should label the campaign action as an assignment when there is no campaign', async () => {
    const fixture = await render(draft);
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '.action-campaign',
    );

    expect(button?.textContent).toContain('Ajouter à une campagne');
    expect(button?.classList.contains('action-campaign-empty')).toBe(true);
  });
});
