import {
  ACCEPTED_MEDIA_CONTENT_TYPES,
  MAX_MEDIA_SIZE_BYTES,
  Media,
  formatFileSize,
  isImageMedia,
  mediaLabel,
  rejectionReason,
} from './media.model';

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

/** Fabrique un fichier d'une taille donnée sans en allouer le contenu. */
function fileOfSize(bytes: number, type: string, name = 'affiche.png'): File {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: bytes });
  return file;
}

describe('media.model', () => {
  describe('mediaLabel', () => {
    it('should prefer the title over the original filename', () => {
      expect(mediaLabel(media())).toBe('Fête de la musique');
    });

    it('should fall back to the filename when the title is empty or absent', () => {
      expect(mediaLabel(media({ title: null }))).toBe('affiche.png');
      expect(mediaLabel(media({ title: '   ' }))).toBe('affiche.png');
    });
  });

  describe('isImageMedia', () => {
    it('should recognize images and exclude PDFs', () => {
      expect(isImageMedia(media())).toBe(true);
      expect(isImageMedia(media({ contentType: 'application/pdf' }))).toBe(false);
    });
  });

  describe('formatFileSize', () => {
    it.each([
      [512, '512 o'],
      [2048, '2 ko'],
      [1_572_864, '1,5 Mo'],
    ] as const)('should format %i bytes as `%s`', (bytes, expected) => {
      expect(formatFileSize(bytes)).toBe(expected);
    });
  });

  describe('rejectionReason', () => {
    it('should accept every format the back accepts', () => {
      for (const type of ACCEPTED_MEDIA_CONTENT_TYPES) {
        expect(rejectionReason(fileOfSize(1024, type))).toBeNull();
      }
    });

    it('should reject an empty file', () => {
      expect(rejectionReason(fileOfSize(0, 'image/png'))).toBe('fichier vide');
    });

    it('should reject a file over the multipart limit', () => {
      const reason = rejectionReason(fileOfSize(MAX_MEDIA_SIZE_BYTES + 1, 'image/png'));
      expect(reason).toContain('trop volumineux');
    });

    it('should reject a format the back would refuse anyway', () => {
      // Le SVG est délibérément exclu : servi en `inline`, il exécuterait son
      // JavaScript dans l'origine de l'application.
      expect(rejectionReason(fileOfSize(1024, 'image/svg+xml'))).toContain('format non accepté');
      expect(rejectionReason(fileOfSize(1024, 'text/plain'))).toContain('format non accepté');
    });
  });
});
