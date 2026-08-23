import { HttpErrorResponse, HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Media } from './media.model';
import { MediasService } from './medias.service';

const media: Media = {
  id: 1,
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
};

describe('MediasService', () => {
  let service: MediasService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(MediasService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should GET the medias list on /api/medias', () => {
    let received: Media[] | undefined;
    service.getMedias().subscribe((list) => (received = list));

    const request = httpTesting.expectOne('/api/medias');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);

    request.flush([media]);
    expect(received).toEqual([media]);
  });

  it('should pass the archived filter as a query parameter when given', () => {
    service.getMedias(false).subscribe();

    const request = httpTesting.expectOne((candidate) => candidate.url === '/api/medias');
    expect(request.request.params.get('archived')).toBe('false');

    request.flush([]);
  });

  it('should derive the binary URL from the media id', () => {
    // L'URL n'est pas stockée en base : une URL absolue persistée deviendrait
    // fausse au premier changement de nom de domaine.
    expect(service.fileUrl(42)).toBe('/api/medias/42/file');
  });

  it('should POST the upload as multipart form data', () => {
    const file = new File(['contenu'], 'affiche.png', { type: 'image/png' });
    service.upload(file, { title: 'Fête', altText: 'Une affiche' }).subscribe();

    const request = httpTesting.expectOne('/api/medias');
    expect(request.request.method).toBe('POST');
    expect(request.request.reportProgress).toBe(true);

    // `FormData.append` réenveloppe le fichier : on compare son identité
    // fonctionnelle, pas la référence.
    const body = request.request.body as FormData;
    const sent = body.get('file') as File;
    expect(sent.name).toBe('affiche.png');
    expect(sent.type).toBe('image/png');
    expect(sent.size).toBe(file.size);
    expect(body.get('title')).toBe('Fête');
    expect(body.get('altText')).toBe('Une affiche');

    request.flush(media);
  });

  it('should omit the optional metadata parts when they are not provided', () => {
    service.upload(new File(['contenu'], 'affiche.png', { type: 'image/png' })).subscribe();

    const request = httpTesting.expectOne('/api/medias');
    const body = request.request.body as FormData;

    expect(body.has('file')).toBe(true);
    expect(body.has('title')).toBe(false);
    expect(body.has('altText')).toBe(false);

    request.flush(media);
  });

  it('should surface upload progress events, not only the final response', () => {
    const types: HttpEventType[] = [];
    service.upload(new File(['contenu'], 'a.png', { type: 'image/png' }))
           .subscribe((event) => types.push(event.type));

    const request = httpTesting.expectOne('/api/medias');
    request.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    request.flush(media);

    expect(types).toContain(HttpEventType.UploadProgress);
    expect(types).toContain(HttpEventType.Response);
  });

  it('should PATCH the metadata of a media', () => {
    service.updateMedia(1, { title: 'Nouveau titre' }).subscribe();

    const request = httpTesting.expectOne('/api/medias/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ title: 'Nouveau titre' });

    request.flush(media);
  });

  it('should DELETE a media', () => {
    service.deleteMedia(1).subscribe();

    const request = httpTesting.expectOne('/api/medias/1');
    expect(request.request.method).toBe('DELETE');

    request.flush(null);
  });

  it('should relay the server error when a format is refused', () => {
    let error: unknown;
    service.upload(new File(['x'], 'a.txt', { type: 'text/plain' }))
           .subscribe({ error: (err) => (error = err) });

    httpTesting.expectOne('/api/medias')
               .flush({ message: 'Format non reconnu.', code: 415 },
                      { status: 415, statusText: 'Unsupported Media Type' });

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(415);
  });
});
