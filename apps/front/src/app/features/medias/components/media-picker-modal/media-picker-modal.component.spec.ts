import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Media } from '../../media.model';
import { MediaPickerModalComponent } from './media-picker-modal.component';

function media(id: number, overrides: Partial<Media> = {}): Media {
  return {
    id,
    title: `Affiche ${ id }`,
    originalFilename: `affiche-${ id }.png`,
    contentType: 'image/png',
    sizeBytes: 1024,
    width: 800,
    height: 600,
    altText: null,
    checksum: `cafe${ id }`,
    archived: false,
    createdAt: '2026-08-23T10:00:00',
    updatedAt: '2026-08-23T10:00:00',
    ...overrides,
  };
}

describe('MediaPickerModalComponent', () => {
  let fixture: ComponentFixture<MediaPickerModalComponent>;
  let compiled: HTMLElement;

  function render(medias: Media[], selectedIds: number[] = [], saving = false): void {
    fixture = TestBed.createComponent(MediaPickerModalComponent);
    fixture.componentRef.setInput('medias', medias);
    fixture.componentRef.setInput('selectedIds', selectedIds);
    fixture.componentRef.setInput('saving', saving);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function items(): HTMLButtonElement[] {
    return Array.from(compiled.querySelectorAll('.picker-item'));
  }

  function submit(): number[] | undefined {
    let emitted: number[] | undefined;
    fixture.componentInstance.submitted.subscribe((ids) => (emitted = ids));
    (compiled.querySelector('.modal-actions .primary') as HTMLButtonElement).click();
    return emitted;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MediaPickerModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should list the library as selectable items', () => {
    render([media(1), media(2)]);

    expect(items().length).toBe(2);
    expect(compiled.querySelector('.picker-thumb img')?.getAttribute('src')).toBe('/api/medias/1/file');
  });

  it('should leave archived medias out of the choice', () => {
    // Archiver sert précisément à retirer un visuel de la sélection.
    render([media(1), media(2, { archived: true })]);

    expect(items().length).toBe(1);
  });

  it('should start from the current selection of the publication', () => {
    render([media(1), media(2)], [2]);

    expect(items()[1].getAttribute('aria-pressed')).toBe('true');
    expect(compiled.querySelector('.picker-order')?.textContent).toContain('Affiche 2');
  });

  it('should add and remove a media on click', () => {
    render([media(1), media(2)]);

    items()[0].click();
    fixture.detectChanges();
    expect(items()[0].getAttribute('aria-pressed')).toBe('true');

    items()[0].click();
    fixture.detectChanges();
    expect(items()[0].getAttribute('aria-pressed')).toBe('false');
    expect(compiled.querySelector('.picker-order')).toBeNull();
  });

  it('should number the selection in the order it was built', () => {
    render([media(1), media(2), media(3)]);

    items()[2].click();
    items()[0].click();
    fixture.detectChanges();

    // L'ordre est porteur de sens : le premier visuel sert de vignette.
    expect(Array.from(compiled.querySelectorAll('.picker-rank')).map((r) => r.textContent?.trim()))
      .toEqual(['1', '2']);
    expect(compiled.querySelector('.picker-name')?.textContent).toContain('Affiche 3');
    expect(submit()).toEqual([3, 1]);
  });

  it('should move a media up and down in the selection', () => {
    render([media(1), media(2)], [1, 2]);

    const down = compiled.querySelectorAll<HTMLButtonElement>('.picker-order li')[0]
                         .querySelectorAll('button')[1];
    down.click();
    fixture.detectChanges();

    expect(submit()).toEqual([2, 1]);
  });

  it('should disable the up arrow on the first item and the down arrow on the last', () => {
    render([media(1), media(2)], [1, 2]);
    const rows = compiled.querySelectorAll('.picker-order li');

    expect((rows[0].querySelectorAll('button')[0] as HTMLButtonElement).disabled).toBe(true);
    expect((rows[1].querySelectorAll('button')[1] as HTMLButtonElement).disabled).toBe(true);
  });

  it('should remove a media from the selection through its remove button', () => {
    render([media(1), media(2)], [1, 2]);

    (compiled.querySelector('.picker-remove') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(submit()).toEqual([2]);
  });

  it('should warn that a PDF will not be published as a photo', () => {
    render([media(1, { contentType: 'application/pdf', title: 'Programme' })], [1]);

    const warning = compiled.querySelector('.picker-warning');
    expect(warning?.textContent).toContain('Programme');
    expect(warning?.textContent).toContain('Non diffusable');
  });

  it('should show a PDF as a label rather than a broken thumbnail', () => {
    render([media(1, { contentType: 'application/pdf' })]);

    expect(compiled.querySelector('.picker-thumb img')).toBeNull();
    expect(compiled.querySelector('.picker-kind')?.textContent).toContain('PDF');
  });

  it('should allow submitting an empty selection to detach everything', () => {
    render([media(1)], [1]);

    (compiled.querySelector('.picker-remove') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(submit()).toEqual([]);
  });

  it('should invite the user to the Médiathèque when the library is empty', () => {
    render([]);

    expect(compiled.querySelector('.picker-empty')?.textContent).toContain('Aucun média disponible');
  });

  it('should not submit twice while the first save is in flight', () => {
    render([media(1)], [1], true);

    expect(submit()).toBeUndefined();
    expect((compiled.querySelector('.modal-actions .primary') as HTMLButtonElement).disabled).toBe(true);
  });

  it('should close on the backdrop, on the close button and on Escape', () => {
    render([media(1)]);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    (compiled.querySelector('.modal-backdrop') as HTMLButtonElement).click();
    (compiled.querySelector('.modal-close') as HTMLButtonElement).click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(closed).toHaveBeenCalledTimes(3);
  });
});
