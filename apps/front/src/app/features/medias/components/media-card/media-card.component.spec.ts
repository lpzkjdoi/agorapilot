import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Media } from '../../media.model';
import { MediaCardComponent } from './media-card.component';

registerLocaleData(localeFr, 'fr-FR');

function media(overrides: Partial<Media> = {}): Media {
  return {
    id: 7,
    title: 'Fête de la musique',
    originalFilename: 'affiche.png',
    contentType: 'image/png',
    sizeBytes: 27904,
    width: 1200,
    height: 1800,
    altText: 'Affiche officielle de la fête',
    checksum: 'cafe',
    archived: false,
    createdAt: '2026-08-23T10:00:00',
    updatedAt: '2026-08-23T10:00:00',
    ...overrides,
  };
}

describe('MediaCardComponent', () => {
  let fixture: ComponentFixture<MediaCardComponent>;

  function render(value: Media = media(), busy = false): HTMLElement {
    fixture = TestBed.createComponent(MediaCardComponent);
    fixture.componentRef.setInput('media', value);
    fixture.componentRef.setInput('busy', busy);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MediaCardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create', () => {
    expect(render()).not.toBeNull();
  });

  it('should show the image thumbnail pointing at the binary endpoint', () => {
    const image = render().querySelector('img');

    expect(image?.getAttribute('src')).toBe('/api/medias/7/file');
    expect(image?.getAttribute('loading')).toBe('lazy');
  });

  it('should use the alt text as the image description', () => {
    expect(render().querySelector('img')?.getAttribute('alt')).toBe('Affiche officielle de la fête');
  });

  it('should leave the alt empty when no alt text was entered', () => {
    // Sans description saisie, la vignette n'apporte rien qu'un lecteur d'écran
    // puisse annoncer : un alt vide la lui fait ignorer.
    expect(render(media({ altText: null })).querySelector('img')?.getAttribute('alt')).toBe('');
  });

  it('should show an icon instead of a thumbnail for a PDF', () => {
    const compiled = render(media({ contentType: 'application/pdf', width: null, height: null }));

    expect(compiled.querySelector('img')).toBeNull();
    expect(compiled.querySelector('.media-kind')?.textContent).toContain('PDF');
  });

  it('should display the title, the size and the dimensions', () => {
    const compiled = render();

    expect(compiled.querySelector('.media-title')?.textContent?.trim()).toBe('Fête de la musique');
    expect(compiled.querySelector('.media-meta')?.textContent).toContain('27 ko');
    expect(compiled.querySelector('.media-meta')?.textContent).toContain('1200 × 1800');
  });

  it('should fall back to the filename when there is no title', () => {
    expect(render(media({ title: null })).querySelector('.media-title')?.textContent?.trim())
      .toBe('affiche.png');
  });

  it('should omit the dimensions when they are unknown', () => {
    const meta = render(media({ width: null, height: null })).querySelector('.media-meta');

    expect(meta?.textContent).not.toContain('×');
  });

  it('should mark an archived media with a badge and offer to unarchive it', () => {
    const compiled = render(media({ archived: true }));

    expect(compiled.querySelector('.media-badge')?.textContent?.trim()).toBe('Archivé');
    expect(compiled.textContent).toContain('Désarchiver');
  });

  it('should emit `renamed` when the edit action is used', () => {
    const compiled = render();
    const renamed = vi.fn();
    fixture.componentInstance.renamed.subscribe(renamed);

    (compiled.querySelectorAll('.media-actions button')[0] as HTMLButtonElement).click();

    expect(renamed).toHaveBeenCalledWith(media());
  });

  it('should emit `archiveToggled` when the archive action is used', () => {
    const compiled = render();
    const toggled = vi.fn();
    fixture.componentInstance.archiveToggled.subscribe(toggled);

    (compiled.querySelectorAll('.media-actions button')[1] as HTMLButtonElement).click();

    expect(toggled).toHaveBeenCalledWith(media());
  });

  it('should ask for a confirmation before emitting a deletion', () => {
    const compiled = render();
    const deleted = vi.fn();
    fixture.componentInstance.deleted.subscribe(deleted);

    (compiled.querySelector('.media-actions .danger') as HTMLButtonElement).click();
    fixture.detectChanges();

    // Premier clic : rien n'est supprimé, la carte demande confirmation.
    expect(deleted).not.toHaveBeenCalled();
    expect(compiled.querySelector('.media-confirm')?.textContent).toContain('Supprimer');

    (compiled.querySelector('.media-actions .danger') as HTMLButtonElement).click();
    expect(deleted).toHaveBeenCalledWith(media());
  });

  it('should let the confirmation be cancelled without deleting', () => {
    const compiled = render();
    const deleted = vi.fn();
    fixture.componentInstance.deleted.subscribe(deleted);

    (compiled.querySelector('.media-actions .danger') as HTMLButtonElement).click();
    fixture.detectChanges();
    (compiled.querySelectorAll('.media-actions button')[0] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(deleted).not.toHaveBeenCalled();
    expect(compiled.querySelector('.media-confirm')).toBeNull();
    expect(compiled.textContent).toContain('Modifier');
  });

  it('should disable its actions while an operation is in flight', () => {
    const compiled = render(media(), true);
    const buttons = compiled.querySelectorAll<HTMLButtonElement>('.media-actions button');

    expect(Array.from(buttons).every((button) => button.disabled)).toBe(true);
  });
});
