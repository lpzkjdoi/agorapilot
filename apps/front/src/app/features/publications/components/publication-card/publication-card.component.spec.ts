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
  campaign: null,
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

  const verified: Publication = { ...draft, status: 'VERIFIED' };

  function el(fixture: ComponentFixture<PublicationCardComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function click(fixture: ComponentFixture<PublicationCardComponent>, selector: string): void {
    const target = el(fixture).querySelector<HTMLElement>(selector);
    expect(target, selector).not.toBeNull();
    target!.click();
    fixture.detectChanges();
  }

  it('should create', async () => {
    const fixture = await render(draft);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // ------------------------------- Texte -------------------------------

  it('should use the first line as the title and the rest as the excerpt', async () => {
    const fixture = await render({ ...draft, content: 'Marché de Noël\nInscriptions ouvertes\njusqu\'au 30 novembre' });

    expect(el(fixture).querySelector('.title')?.textContent?.trim()).toBe('Marché de Noël');
    expect(el(fixture).querySelector('.excerpt')?.textContent?.trim())
      .toBe('Inscriptions ouvertes jusqu\'au 30 novembre');
  });

  it('should cut a single block after its first sentence', async () => {
    const fixture = await render({ ...draft, content: 'Collecte des encombrants le 24 novembre. Sortez vos objets la veille.' });

    expect(el(fixture).querySelector('.title')?.textContent?.trim()).toBe('Collecte des encombrants le 24 novembre.');
    expect(el(fixture).querySelector('.excerpt')?.textContent?.trim()).toBe('Sortez vos objets la veille.');
  });

  it('should keep a short single line as the title, without excerpt', async () => {
    const fixture = await render(draft);

    expect(el(fixture).querySelector('.title')?.textContent?.trim()).toBe('Fermeture exceptionnelle de la mairie');
    expect(el(fixture).querySelector('.excerpt')).toBeNull();
  });

  it('should render the `Brouillon` badge of a draft', async () => {
    const badge = el(await render(draft)).querySelector('.status');

    expect(badge?.textContent?.trim()).toBe('Brouillon');
    expect(badge?.classList.contains('status-draft')).toBe(true);
  });

  it('should render the `Vérifiée` badge of a verified publication', async () => {
    const badge = el(await render(verified)).querySelector('.status');

    expect(badge?.textContent?.trim()).toBe('Vérifiée');
    expect(badge?.classList.contains('status-verified')).toBe(true);
  });

  // ------------------------------ Campagne ------------------------------

  it('should offer to attach a campaign from the tag when there is none', async () => {
    const fixture = await render(draft);
    const assign = vi.fn();
    fixture.componentInstance.assignCampaign.subscribe(assign);
    const tag = el(fixture).querySelector('.campaign');

    expect(tag?.textContent).toContain('+ Campagne');
    expect(tag?.classList.contains('campaign-empty')).toBe(true);
    click(fixture, '.campaign');
    expect(assign).toHaveBeenCalledWith(draft);
  });

  it('should render the campaign name as a tag that changes it', async () => {
    const fixture = await render({ ...draft, campaign: { id: 7, name: 'Parcs & Loisirs' } });
    const tag = el(fixture).querySelector('.campaign');

    expect(tag?.textContent).toContain('Parcs & Loisirs');
    expect(tag?.getAttribute('aria-label')).toContain('Changer de campagne');
  });

  it('should show the campaign as plain text when the account cannot change it', async () => {
    const fixture = TestBed.createComponent(PublicationCardComponent);
    fixture.componentRef.setInput('publication', { ...draft, campaign: { id: 7, name: 'Parcs' } });
    fixture.componentRef.setInput('canAssignCampaign', false);
    fixture.detectChanges();

    expect(el(fixture).querySelector('span.campaign')?.textContent).toContain('Parcs');
    expect(el(fixture).querySelector('button.campaign')).toBeNull();
  });

  // ------------------------------ Traçabilité ------------------------------

  it('should name the author, and whoever modified it since', async () => {
    const created = await render({ ...draft, createdAt: '2026-03-20T10:30:00', createdBy: 'camille',
                                   updatedAt: '2026-03-20T10:30:00', updatedBy: 'camille' });
    expect(el(created).querySelector('.date')?.textContent).toContain('par camille');
    expect(el(created).querySelector('.date')?.textContent).not.toContain('modifiée');

    const modified = await render({ ...draft, createdAt: '2026-03-20T10:30:00', createdBy: 'camille',
                                    updatedAt: '2026-03-21T09:00:00', updatedBy: 'bruno' });
    expect(el(modified).querySelector('.date')?.textContent).toContain('modifiée par bruno');

    const byAuthor = await render({ ...draft, createdAt: '2026-03-20T10:30:00', createdBy: 'camille',
                                    updatedAt: '2026-03-21T09:00:00', updatedBy: 'camille' });
    const text = el(byAuthor).querySelector('.date')?.textContent ?? '';
    expect(text).toContain('modifiée');
    expect(text).not.toContain('modifiée par');
  });

  it('should not render the date line for a publication without creation date', async () => {
    expect(el(await render(draft)).querySelector('.date')).toBeNull();
  });

  it('should format `createdAt` in French when the API exposes it', async () => {
    const date = el(await render({ ...draft, createdAt: '2026-03-20T10:30:00' })).querySelector('.date');

    expect(date?.textContent).toContain('20 mars 2026');
    expect(date?.textContent).toContain('10:30');
  });

  // ------------------------------ Diffusion ------------------------------

  it('should publish on Facebook from the `Diffuser` menu', async () => {
    const fixture = await render(verified);
    const facebook = vi.fn();
    fixture.componentInstance.publishOnFacebook.subscribe(facebook);

    expect(el(fixture).querySelector('.action-facebook')).toBeNull();
    click(fixture, '.action-diffuse');
    expect(el(fixture).querySelector('.action-diffuse')?.getAttribute('aria-expanded')).toBe('true');
    click(fixture, '.action-facebook');

    expect(facebook).toHaveBeenCalledWith(verified);
    expect(el(fixture).querySelector('[role="menu"]')).toBeNull();
  });

  it('should schedule from the `Diffuser` menu', async () => {
    const fixture = await render(verified);
    const schedule = vi.fn();
    fixture.componentInstance.schedule.subscribe(schedule);

    click(fixture, '.action-diffuse');
    expect(el(fixture).querySelector('.action-schedule')?.textContent).toContain('Programmer');
    click(fixture, '.action-schedule');

    expect(schedule).toHaveBeenCalledWith(verified);
  });

  it('should offer to review a draft instead of diffusing it, saying why', async () => {
    const fixture = await render(draft);
    const edit = vi.fn();
    fixture.componentInstance.edit.subscribe(edit);

    expect(el(fixture).querySelector('.action-diffuse')).toBeNull();
    expect(el(fixture).querySelector('.hint')?.textContent).toContain('une fois la publication vérifiée');
    click(fixture, '.action-review');

    expect(edit).toHaveBeenCalledWith(draft);
  });

  it('should show a locked pending button while publishing', async () => {
    const fixture = await render(verified, true);
    const button = el(fixture).querySelector<HTMLButtonElement>('.action-publishing');

    expect(button?.disabled).toBe(true);
    expect(button?.textContent).toContain('Publication sur Facebook…');
    expect(button?.querySelector('app-loader')).not.toBeNull();
    expect(el(fixture).querySelector('.action-diffuse')).toBeNull();
  });

  // ---------------------------- Autres actions ----------------------------

  it('should emit `edit` from the pencil of a verified publication', async () => {
    const fixture = await render(verified);
    const edit = vi.fn();
    fixture.componentInstance.edit.subscribe(edit);

    click(fixture, '.action-edit');

    expect(edit).toHaveBeenCalledWith(verified);
  });

  it('should gather the secondary actions in the `⋯` menu', async () => {
    const fixture = await render({ ...verified, medias: [image(4), image(5)] });
    const component = fixture.componentInstance;
    const xlsx = vi.fn();
    const medias = vi.fn();
    const campaign = vi.fn();
    component.generateXlsx.subscribe(xlsx);
    component.manageMedias.subscribe(medias);
    component.assignCampaign.subscribe(campaign);

    click(fixture, '.action-more');
    expect(el(fixture).querySelector('.action-medias')?.textContent).toContain('Gérer les visuels (2)');
    expect(el(fixture).querySelector('.action-campaign')?.textContent).toContain('Ajouter à une campagne');
    click(fixture, '.action-xlsx');
    click(fixture, '.action-more');
    click(fixture, '.action-medias');
    click(fixture, '.action-more');
    click(fixture, '.action-campaign');

    expect(xlsx).toHaveBeenCalledTimes(1);
    expect(medias).toHaveBeenCalledTimes(1);
    expect(campaign).toHaveBeenCalledTimes(1);
  });

  it('should keep a single menu open at a time', async () => {
    const fixture = await render(verified);

    click(fixture, '.action-diffuse');
    click(fixture, '.action-more');

    expect(el(fixture).querySelectorAll('[role="menu"]').length).toBe(1);
    expect(el(fixture).querySelector('.action-xlsx')).not.toBeNull();
  });

  it('should close the menu on Escape and on a click outside the card', async () => {
    const fixture = await render(verified);

    click(fixture, '.action-more');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(el(fixture).querySelector('[role="menu"]')).toBeNull();

    click(fixture, '.action-more');
    document.body.click();
    fixture.detectChanges();
    expect(el(fixture).querySelector('[role="menu"]')).toBeNull();
  });

  it('should offer only the actions the account may perform', async () => {
    const fixture = TestBed.createComponent(PublicationCardComponent);
    fixture.componentRef.setInput('publication', verified);
    for (const input of ['canEdit', 'canDelete', 'canPublish', 'canSchedule', 'canManageMedias', 'canAssignCampaign']) {
      fixture.componentRef.setInput(input, false);
    }
    fixture.detectChanges();

    for (const selector of ['.action-diffuse', '.action-review', '.action-edit', '.campaign', '.media-add']) {
      expect(el(fixture).querySelector(selector), selector).toBeNull();
    }
    click(fixture, '.action-more');
    for (const selector of ['.action-delete', '.action-medias', '.action-campaign']) {
      expect(el(fixture).querySelector(selector), selector).toBeNull();
    }
    expect(el(fixture).querySelector('.action-xlsx')).not.toBeNull();
  });

  // ------------------------------- Visuels -------------------------------

  it('should offer to add a visual when nothing is attached', async () => {
    const fixture = await render(draft);
    const manage = vi.fn();
    fixture.componentInstance.manageMedias.subscribe(manage);

    expect(el(fixture).querySelector('.media')).toBeNull();
    click(fixture, '.media-add');

    expect(manage).toHaveBeenCalledWith(draft);
  });

  it('should use the first attached image as the thumbnail, the whole visual leading to the media', async () => {
    const fixture = await render({ ...draft, medias: [image(4), image(5)] });
    const manage = vi.fn();
    fixture.componentInstance.manageMedias.subscribe(manage);
    const thumbnail = el(fixture).querySelector('.media-thumbnail');

    expect(thumbnail?.getAttribute('src')).toBe('/api/medias/4/file');
    expect(thumbnail?.getAttribute('loading')).toBe('lazy');
    expect(el(fixture).querySelector('.media-button')?.getAttribute('aria-label')).toBe('Gérer les visuels (2)');
    click(fixture, '.media-button');
    expect(manage).toHaveBeenCalledTimes(1);
  });

  it('should skip a leading PDF and use the first real image', async () => {
    // Un PDF ne peut pas servir de vignette ; la carte prend l'image suivante.
    const fixture = await render({
      ...draft,
      medias: [{ ...image(4), contentType: 'application/pdf' }, image(5)],
    });

    expect(el(fixture).querySelector('.media-thumbnail')?.getAttribute('src')).toBe('/api/medias/5/file');
  });

  it('should fall back to the icon when only a PDF is attached', async () => {
    const fixture = await render({ ...draft, medias: [{ ...image(4), contentType: 'application/pdf' }] });

    expect(el(fixture).querySelector('.media-thumbnail')).toBeNull();
    expect(el(fixture).querySelector('.media .media-placeholder')).not.toBeNull();
  });

  it('should not make the visual a button when the account cannot manage media', async () => {
    const fixture = TestBed.createComponent(PublicationCardComponent);
    fixture.componentRef.setInput('publication', { ...draft, medias: [image(4)] });
    fixture.componentRef.setInput('canManageMedias', false);
    fixture.detectChanges();

    expect(el(fixture).querySelector('div.media .media-thumbnail')).not.toBeNull();
    expect(el(fixture).querySelector('.media-button')).toBeNull();
  });

  it('should describe the thumbnail with the alt text, and leave it empty without one', async () => {
    const described = await render({ ...draft, medias: [{ ...image(4), altText: 'Affiche du marché' }] });
    expect(el(described).querySelector('.media-thumbnail')?.getAttribute('alt')).toBe('Affiche du marché');

    const plain = await render({ ...draft, medias: [image(4)] });
    expect(el(plain).querySelector('.media-thumbnail')?.getAttribute('alt')).toBe('');
  });

  it('should count the visuals when there are several', async () => {
    const fixture = await render({ ...draft, medias: [image(4), image(5), image(6)] });

    expect(el(fixture).querySelector('.media-count')?.textContent?.trim()).toBe('3');
  });

  it('should not show a counter for a single visual', async () => {
    expect(el(await render({ ...draft, medias: [image(4)] })).querySelector('.media-count')).toBeNull();
  });

  // ------------------------------ Suppression ------------------------------

  it('should ask for a confirmation before emitting `remove`', async () => {
    const fixture = await render(draft);
    const remove = vi.fn();
    fixture.componentInstance.remove.subscribe(remove);

    click(fixture, '.action-more');
    click(fixture, '.action-delete');

    expect(remove).not.toHaveBeenCalled();
    expect(el(fixture).querySelector('[role="menu"]')).toBeNull();
    expect(el(fixture).querySelector('.confirm')?.textContent).toContain('diffusions programmées seront annulées');

    click(fixture, '.action-confirm-delete');

    expect(remove).toHaveBeenCalledWith(draft);
    expect(el(fixture).querySelector('.confirm')).toBeNull();
  });

  it('should drop the confirmation when it is cancelled', async () => {
    const fixture = await render(draft);
    const remove = vi.fn();
    fixture.componentInstance.remove.subscribe(remove);

    click(fixture, '.action-more');
    click(fixture, '.action-delete');
    click(fixture, '.action-cancel');

    expect(remove).not.toHaveBeenCalled();
    expect(el(fixture).querySelector('.confirm')).toBeNull();
    expect(el(fixture).querySelector('.action-review')).not.toBeNull();
  });

  it('should show a locked pending button while deleting', async () => {
    const fixture = TestBed.createComponent(PublicationCardComponent);
    fixture.componentRef.setInput('publication', verified);
    fixture.componentRef.setInput('deleting', true);
    fixture.detectChanges();

    const button = el(fixture).querySelector<HTMLButtonElement>('.solid-button-pending');
    expect(button?.disabled).toBe(true);
    expect(button?.textContent).toContain('Suppression…');
    expect(el(fixture).querySelector('.action-diffuse')).toBeNull();
  });
});
