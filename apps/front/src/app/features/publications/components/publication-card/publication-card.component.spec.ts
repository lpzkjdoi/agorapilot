import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Publication, PublicationMedia } from '../../publication.model';
import { PublicationCardComponent } from './publication-card.component';

registerLocaleData(localeFr, 'fr-FR');

const draft: Publication = {
  id: 1,
  content: 'Fermeture exceptionnelle de la mairie',
  status: 'DRAFT',
  medias: [],
};

function image(id: number): PublicationMedia {
  return {
    id,
    title: `Affiche ${ id }`,
    originalFilename: `affiche-${ id }.png`,
    contentType: 'image/png',
    width: 1200,
    height: 1800,
    altText: null,
  };
}

async function render(
  publication: Publication,
  publishing = false,
): Promise<ComponentFixture<PublicationCardComponent>> {
  const fixture = TestBed.createComponent(PublicationCardComponent);
  fixture.componentRef.setInput('publication', publication);
  fixture.componentRef.setInput('publishing', publishing);
  fixture.detectChanges();
  return fixture;
}

describe('PublicationCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicationCardComponent],
      // La carte dérive l'URL de sa vignette via `MediasService`, qui injecte
      // `HttpClient` — aucune requête n'est émise pour autant.
      providers: [provideHttpClient(), provideHttpClientTesting()],
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

  it('should show the loader and lock the Facebook button while publishing', async () => {
    const fixture = await render(draft, true);
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.action-facebook',
    );

    expect(button?.disabled).toBe(true);
    expect(button?.textContent).toContain('Publication…');
    expect(button?.querySelector('app-loader')).not.toBeNull();
  });

  it('should not emit the Facebook action while a publication is already in flight', async () => {
    const fixture = await render(draft, true);
    const facebook = vi.fn();
    fixture.componentInstance.publishOnFacebook.subscribe(facebook);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.action-facebook')
      ?.click();

    expect(facebook).not.toHaveBeenCalled();
  });

  it('should label the campaign action as an assignment when there is no campaign', async () => {
    const fixture = await render(draft);
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '.action-campaign',
    );

    expect(button?.textContent).toContain('Ajouter à une campagne');
    expect(button?.classList.contains('action-campaign-empty')).toBe(true);
  });

  // ------------------------------- Visuels -------------------------------

  it('should keep the `no media` icon when nothing is attached', async () => {
    const fixture = await render(draft);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.media img')).toBeNull();
    expect(compiled.querySelector('.media svg')).not.toBeNull();
  });

  it('should use the first attached image as the thumbnail', async () => {
    const fixture = await render({ ...draft, medias: [image(4), image(5)] });
    const thumbnail = (fixture.nativeElement as HTMLElement).querySelector('.media-thumbnail');

    expect(thumbnail?.getAttribute('src')).toBe('/api/medias/4/file');
    expect(thumbnail?.getAttribute('loading')).toBe('lazy');
  });

  it('should skip a leading PDF and use the first real image', async () => {
    // Un PDF ne peut pas servir de vignette ; la carte prend l'image suivante.
    const fixture = await render({
      ...draft,
      medias: [{ ...image(4), contentType: 'application/pdf' }, image(5)],
    });

    expect((fixture.nativeElement as HTMLElement).querySelector('.media-thumbnail')?.getAttribute('src'))
      .toBe('/api/medias/5/file');
  });

  it('should fall back to the icon when only a PDF is attached', async () => {
    const fixture = await render({
      ...draft,
      medias: [{ ...image(4), contentType: 'application/pdf' }],
    });

    expect((fixture.nativeElement as HTMLElement).querySelector('.media-thumbnail')).toBeNull();
  });

  it('should describe the thumbnail with the alt text, and leave it empty without one', async () => {
    const described = await render({ ...draft, medias: [{ ...image(4), altText: 'Affiche du marché' }] });
    expect((described.nativeElement as HTMLElement).querySelector('.media-thumbnail')?.getAttribute('alt'))
      .toBe('Affiche du marché');

    const plain = await render({ ...draft, medias: [image(4)] });
    expect((plain.nativeElement as HTMLElement).querySelector('.media-thumbnail')?.getAttribute('alt'))
      .toBe('');
  });

  it('should count the extra visuals beyond the thumbnail', async () => {
    const fixture = await render({ ...draft, medias: [image(4), image(5), image(6)] });

    expect((fixture.nativeElement as HTMLElement).querySelector('.media-count')?.textContent?.trim())
      .toBe('+2');
  });

  it('should not show a counter for a single visual', async () => {
    const fixture = await render({ ...draft, medias: [image(4)] });

    expect((fixture.nativeElement as HTMLElement).querySelector('.media-count')).toBeNull();
  });

  it('should label the media action by the number attached', async () => {
    const empty = await render(draft);
    expect((empty.nativeElement as HTMLElement).querySelector('.action-medias')?.textContent)
      .toContain('Ajouter un visuel');

    const filled = await render({ ...draft, medias: [image(4), image(5)] });
    expect((filled.nativeElement as HTMLElement).querySelector('.action-medias')?.textContent)
      .toContain('Modifier les visuels (2)');
  });

  it('should emit `manageMedias` when the media action is used', async () => {
    const fixture = await render(draft);
    const manage = vi.fn();
    fixture.componentInstance.manageMedias.subscribe(manage);

    ((fixture.nativeElement as HTMLElement).querySelector('.action-medias') as HTMLButtonElement).click();

    expect(manage).toHaveBeenCalledWith(draft);
  });
});
