import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Media, UpdateMediaRequest } from '../../media.model';
import { MediaEditModalComponent } from './media-edit-modal.component';

function media(overrides: Partial<Media> = {}): Media {
  return {
    id: 7,
    title: 'Fête de la musique',
    originalFilename: 'affiche.png',
    contentType: 'image/png',
    sizeBytes: 27904,
    width: 1200,
    height: 1800,
    altText: 'Affiche officielle',
    checksum: 'cafe',
    archived: false,
    createdAt: '2026-08-23T10:00:00',
    updatedAt: '2026-08-23T10:00:00',
    ...overrides,
  };
}

describe('MediaEditModalComponent', () => {
  let fixture: ComponentFixture<MediaEditModalComponent>;
  let compiled: HTMLElement;

  function render(value: Media = media(), saving = false): void {
    fixture = TestBed.createComponent(MediaEditModalComponent);
    fixture.componentRef.setInput('media', value);
    fixture.componentRef.setInput('saving', saving);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function field(name: string): HTMLInputElement | HTMLTextAreaElement {
    return compiled.querySelector(`[formControlName="${ name }"]`)!;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MediaEditModalComponent],
    }).compileComponents();
  });

  it('should create', () => {
    render();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should prefill the form with the current metadata', () => {
    render();

    expect(field('title').value).toBe('Fête de la musique');
    expect(field('altText').value).toBe('Affiche officielle');
  });

  it('should start from empty fields when the media has no metadata yet', () => {
    render(media({ title: null, altText: null }));

    expect(field('title').value).toBe('');
    expect(field('altText').value).toBe('');
  });

  it('should show the media label as the subtitle', () => {
    render(media({ title: null }));

    expect(compiled.querySelector('.modal-subtitle')?.textContent?.trim()).toBe('affiche.png');
  });

  it('should emit the trimmed values on submit', () => {
    render();
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);

    const title = field('title');
    title.value = '  Nouveau titre  ';
    title.dispatchEvent(new Event('input'));
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(submitted).toHaveBeenCalledWith<[UpdateMediaRequest]>({
      title: 'Nouveau titre',
      altText: 'Affiche officielle',
    });
  });

  it('should let a field be cleared', () => {
    // Une chaîne vide est bien envoyée : c'est ce qui permet d'effacer un titre.
    render();
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);

    const title = field('title');
    title.value = '';
    title.dispatchEvent(new Event('input'));
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ title: '' }));
  });

  it('should refuse to submit a title over the server limit', () => {
    render();
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);

    const title = field('title');
    title.value = 'a'.repeat(256);
    title.dispatchEvent(new Event('input'));
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(submitted).not.toHaveBeenCalled();
    expect(compiled.querySelector('.media-form-error')?.textContent).toContain('255 caractères');
  });

  it('should not submit twice while the first save is in flight', () => {
    render(media(), true);
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);

    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(submitted).not.toHaveBeenCalled();
    expect((compiled.querySelector('.primary') as HTMLButtonElement).disabled).toBe(true);
  });

  it('should close on the backdrop, on the close button and on Escape', () => {
    render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    (compiled.querySelector('.modal-backdrop') as HTMLButtonElement).click();
    (compiled.querySelector('.modal-close') as HTMLButtonElement).click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(closed).toHaveBeenCalledTimes(3);
  });
});
