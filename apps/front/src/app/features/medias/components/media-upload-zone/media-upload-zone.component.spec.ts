import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAX_MEDIA_SIZE_BYTES } from '../../media.model';
import { FileSelection, MediaUploadZoneComponent } from './media-upload-zone.component';

/** Fabrique un fichier d'une taille donnée sans en allouer le contenu. */
function fileOfSize(bytes: number, type: string, name: string): File {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: bytes });
  return file;
}

/** DragEvent n'existe pas dans jsdom : un Event porteur de `dataTransfer` suffit. */
function dropEvent(files: File[]): Event {
  const event = new Event('drop') as Event & { dataTransfer: { files: File[] } };
  Object.defineProperty(event, 'dataTransfer', { value: { files } });
  return event;
}

describe('MediaUploadZoneComponent', () => {
  let fixture: ComponentFixture<MediaUploadZoneComponent>;
  let compiled: HTMLElement;
  let selections: FileSelection[];

  function render(uploading = false, progress: number | null = null): void {
    fixture = TestBed.createComponent(MediaUploadZoneComponent);
    fixture.componentRef.setInput('uploading', uploading);
    fixture.componentRef.setInput('progress', progress);
    selections = [];
    fixture.componentInstance.filesSelected.subscribe((s) => selections.push(s));
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MediaUploadZoneComponent],
    }).compileComponents();
  });

  it('should create', () => {
    render();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should restrict the file picker to the formats the back accepts', () => {
    render();
    const input = compiled.querySelector('input[type="file"]');

    expect(input?.getAttribute('accept')).toBe('image/jpeg,image/png,image/webp,application/pdf');
    expect(input?.hasAttribute('multiple')).toBe(true);
  });

  it('should announce the size limit to the user', () => {
    render();
    expect(compiled.querySelector('.upload-hint')?.textContent).toContain('15,0 Mo');
  });

  it('should emit the accepted files picked through the input', () => {
    render();
    const file = fileOfSize(1024, 'image/png', 'affiche.png');
    const input = compiled.querySelector('input[type="file"]') as HTMLInputElement;

    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));

    expect(selections).toEqual([{ accepted: [file], rejected: [] }]);
  });

  it('should emit the files dropped on the zone', () => {
    render();
    const file = fileOfSize(2048, 'application/pdf', 'affiche.pdf');

    compiled.querySelector('.upload-zone')!.dispatchEvent(dropEvent([file]));

    expect(selections[0].accepted).toEqual([file]);
  });

  it('should separate rejected files from accepted ones instead of dropping them silently', () => {
    render();
    const accepted = fileOfSize(1024, 'image/png', 'affiche.png');
    const tooBig = fileOfSize(MAX_MEDIA_SIZE_BYTES + 1, 'image/png', 'enorme.png');
    const wrongType = fileOfSize(1024, 'image/svg+xml', 'logo.svg');

    compiled.querySelector('.upload-zone')!.dispatchEvent(dropEvent([accepted, tooBig, wrongType]));

    expect(selections[0].accepted).toEqual([accepted]);
    expect(selections[0].rejected.map((r) => r.filename)).toEqual(['enorme.png', 'logo.svg']);
    expect(selections[0].rejected[0].reason).toContain('trop volumineux');
    expect(selections[0].rejected[1].reason).toContain('format non accepté');
  });

  it('should not emit anything for an empty drop', () => {
    render();
    compiled.querySelector('.upload-zone')!.dispatchEvent(dropEvent([]));

    expect(selections).toEqual([]);
  });

  it('should ignore a drop while an upload is already running', () => {
    render(true, 40);
    compiled.querySelector('.upload-zone')!
            .dispatchEvent(dropEvent([fileOfSize(1024, 'image/png', 'a.png')]));

    expect(selections).toEqual([]);
  });

  it('should highlight the zone while a file is dragged over it', () => {
    render();
    const zone = compiled.querySelector('.upload-zone')!;

    zone.dispatchEvent(new Event('dragover'));
    fixture.detectChanges();
    expect(zone.classList.contains('dragging')).toBe(true);

    zone.dispatchEvent(new Event('dragleave'));
    fixture.detectChanges();
    expect(zone.classList.contains('dragging')).toBe(false);
  });

  it('should show the progress bar instead of the picker while uploading', () => {
    render(true, 40);
    const bar = compiled.querySelector('.upload-progress-bar');

    expect(compiled.querySelector('input[type="file"]')).toBeNull();
    expect(bar?.getAttribute('aria-valuenow')).toBe('40');
    expect((bar as HTMLElement).style.width).toBe('40%');
  });

  it('should reset the input so the same file can be picked twice in a row', () => {
    render();
    const input = compiled.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      value: [fileOfSize(1024, 'image/png', 'affiche.png')],
      configurable: true,
    });

    input.dispatchEvent(new Event('change'));

    expect(input.value).toBe('');
  });
});
