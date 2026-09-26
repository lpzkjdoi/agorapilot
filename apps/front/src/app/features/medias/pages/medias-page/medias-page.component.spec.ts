import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { Media } from '../../media.model';
import { MediasPageComponent } from './medias-page.component';

registerLocaleData(localeFr, 'fr-FR');

function media(overrides: Partial<Media> = {}): Media {
  return {
    id: 1,
    title: 'Fête de la musique',
    originalFilename: 'affiche.png',
    contentType: 'image/png',
    sizeBytes: 27904,
    width: 1200,
    height: 1800,
    altText: null,
    checksum: 'cafe',
    archived: false,
    createdAt: '2026-08-23T10:00:00',
    updatedAt: '2026-08-23T10:00:00',
    ...overrides,
  };
}

function fileOfSize(bytes: number, type: string, name: string): File {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: bytes });
  return file;
}

function dropEvent(files: File[]): Event {
  const event = new Event('drop') as Event & { dataTransfer: { files: File[] } };
  Object.defineProperty(event, 'dataTransfer', { value: { files } });
  return event;
}

describe('MediasPageComponent', () => {
  let fixture: ComponentFixture<MediasPageComponent>;
  let compiled: HTMLElement;
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;

  /** Crée la page et satisfait la requête de chargement initiale. */
  function render(medias: Media[] = [media()]): void {
    fixture = TestBed.createComponent(MediasPageComponent);
    fixture.detectChanges();
    httpTesting.expectOne('/api/medias').flush(medias);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function drop(files: File[]): void {
    compiled.querySelector('.upload-zone')!.dispatchEvent(dropEvent(files));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MediasPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  // ------------------------------ Chargement -----------------------------

  it('should render the loaded medias as cards', () => {
    render([media(), media({ id: 2, title: 'Marché de Noël' })]);

    expect(compiled.querySelectorAll('app-media-card').length).toBe(2);
    expect(compiled.querySelector('.medias-count')?.textContent).toContain('2');
  });

  it('should show a loader while the list is in flight', () => {
    fixture = TestBed.createComponent(MediasPageComponent);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('app-loader')).not.toBeNull();

    httpTesting.expectOne('/api/medias').flush([]);
  });

  it('should invite the user to upload when the library is empty', () => {
    render([]);

    expect(compiled.querySelector('.medias-placeholder')?.textContent)
      .toContain('Aucun média pour le moment');
  });

  it('should show a blocking message when the list cannot be loaded', () => {
    fixture = TestBed.createComponent(MediasPageComponent);
    fixture.detectChanges();
    httpTesting.expectOne('/api/medias')
               .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;

    const error = compiled.querySelector('.medias-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(error?.textContent).toContain('n’ont pas pu être chargés');
  });

  // -------------------------------- Dépôt --------------------------------

  it('should upload a dropped file and prepend it to the list', () => {
    render([]);
    drop([fileOfSize(1024, 'image/png', 'affiche.png')]);

    const request = httpTesting.expectOne('/api/medias');
    expect(request.request.method).toBe('POST');
    request.flush(media({ id: 9, title: 'Nouvelle affiche' }));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-media-card').length).toBe(1);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'success', message: 'Média ajouté.' }),
    ]);
  });

  it('should upload several files one after the other rather than all at once', () => {
    render([]);
    drop([
      fileOfSize(1024, 'image/png', 'une.png'),
      fileOfSize(1024, 'image/png', 'deux.png'),
    ]);

    // Un seul envoi en vol : le VPS est en 1 vCPU et la progression n'a de sens
    // que pour un fichier à la fois.
    const first = httpTesting.expectOne('/api/medias');
    first.flush(media({ id: 9 }));
    fixture.detectChanges();

    const second = httpTesting.expectOne('/api/medias');
    second.flush(media({ id: 10 }));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-media-card').length).toBe(2);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ message: '2 médias ajoutés.' }),
    ]);
  });

  it('should show the upload progress reported by the server', () => {
    render([]);
    drop([fileOfSize(1024, 'image/png', 'affiche.png')]);

    const request = httpTesting.expectOne('/api/medias');
    request.event({ type: HttpEventType.UploadProgress, loaded: 30, total: 100 });
    fixture.detectChanges();

    expect(compiled.querySelector('.upload-progress-bar')?.getAttribute('aria-valuenow')).toBe('30');

    request.flush(media({ id: 9 }));
  });

  it('should warn about locally rejected files without calling the server', () => {
    render([]);
    drop([fileOfSize(1024, 'image/svg+xml', 'logo.svg')]);

    httpTesting.expectNone('/api/medias');
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'warning', message: expect.stringContaining('logo.svg') }),
    ]);
  });

  it('should relay the server message when a format is refused', () => {
    render([]);
    drop([fileOfSize(1024, 'image/png', 'affiche.png')]);

    httpTesting.expectOne('/api/medias')
               .flush({ message: 'Format non reconnu.', code: 415 },
                      { status: 415, statusText: 'Unsupported Media Type' });
    fixture.detectChanges();

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'error', message: 'Format non reconnu.' }),
    ]);
    // La zone redevient disponible après un échec.
    expect(compiled.querySelector('input[type="file"]')).not.toBeNull();
  });

  it('should explain a 413 in terms of size rather than format', () => {
    render([]);
    drop([fileOfSize(1024, 'image/png', 'affiche.png')]);

    httpTesting.expectOne('/api/medias')
               .flush({ message: '…', code: 413 }, { status: 413, statusText: 'Payload Too Large' });

    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ message: expect.stringContaining('taille maximale') }),
    ]);
  });

  // ------------------------ Édition et archivage --------------------------

  it('should open the edit modal and save the new metadata', () => {
    render();
    (compiled.querySelectorAll('.media-actions button')[0] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(compiled.querySelector('app-media-edit-modal')).not.toBeNull();

    const title = compiled.querySelector('[formControlName="title"]') as HTMLInputElement;
    title.value = 'Nouveau titre';
    title.dispatchEvent(new Event('input'));
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));

    const request = httpTesting.expectOne('/api/medias/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ title: 'Nouveau titre', altText: '' });
    request.flush(media({ title: 'Nouveau titre' }));
    fixture.detectChanges();

    expect(compiled.querySelector('app-media-edit-modal')).toBeNull();
    expect(compiled.querySelector('.media-title')?.textContent?.trim()).toBe('Nouveau titre');
  });

  it('should archive a media and move it out of the current tab', () => {
    render();
    (compiled.querySelectorAll('.media-actions button')[1] as HTMLButtonElement).click();

    const request = httpTesting.expectOne('/api/medias/1');
    expect(request.request.body).toEqual({ archived: true });
    request.flush(media({ archived: true }));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-media-card').length).toBe(0);
    expect(compiled.querySelector('.medias-tabs')?.textContent).toContain('Archivés (1)');
  });

  it('should show archived medias in their own tab', () => {
    render([media(), media({ id: 2, archived: true })]);

    expect(compiled.querySelectorAll('app-media-card').length).toBe(1);

    (compiled.querySelectorAll('.medias-tabs button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-media-card').length).toBe(1);
    expect(compiled.querySelector('.media-badge')?.textContent?.trim()).toBe('Archivé');
  });

  // ---------------------------- Suppression -------------------------------

  it('should delete a media once the deletion is confirmed', () => {
    render();
    const danger = () => compiled.querySelector('.media-actions .danger') as HTMLButtonElement;

    danger().click();
    fixture.detectChanges();
    danger().click();

    const request = httpTesting.expectOne('/api/medias/1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-media-card').length).toBe(0);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ level: 'success', message: 'Fête de la musique supprimé.' }),
    ]);
  });

  it('should explain a 409 by the media being used, and keep the card', () => {
    render();
    const danger = () => compiled.querySelector('.media-actions .danger') as HTMLButtonElement;

    danger().click();
    fixture.detectChanges();
    danger().click();

    httpTesting.expectOne('/api/medias/1')
               .flush({ message: '…', code: 409 }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-media-card').length).toBe(1);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({ message: expect.stringContaining('utilisé par des publications') }),
    ]);
  });
});
