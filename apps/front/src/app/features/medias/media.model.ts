/** Formats acceptés par le back (`agorapilot.media.allowed-content-types`). */
export const ACCEPTED_MEDIA_CONTENT_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'application/pdf',
] as const;

export type MediaContentType = (typeof ACCEPTED_MEDIA_CONTENT_TYPES)[number];

/**
 * Doit rester aligné sur `spring.servlet.multipart.max-file-size` et sur le
 * `client_max_body_size` du nginx du front. Le contrôle côté navigateur n'est
 * qu'un confort : il évite d'envoyer 20 Mo pour se faire répondre 413.
 */
export const MAX_MEDIA_SIZE_BYTES = 15 * 1024 * 1024;

/** Valeur de l'attribut `accept` d'un `<input type="file">`. */
export const MEDIA_ACCEPT_ATTRIBUTE = ACCEPTED_MEDIA_CONTENT_TYPES.join(',');

export interface Media {
  id: number;
  title: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  /** Nuls pour un PDF, et pour une image dont l'en-tête était illisible. */
  width: number | null;
  height: number | null;
  altText: string | null;
  checksum: string;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateMediaRequest {
  title?: string | null;
  altText?: string | null;
  archived?: boolean;
}

export interface MediaUploadMetadata {
  title?: string;
  altText?: string;
}

/** Refus prononcé côté navigateur, avant tout appel réseau. */
export interface RejectedFile {
  filename: string;
  reason: string;
}

export function isImageMedia(media: Media): boolean {
  return media.contentType.startsWith('image/');
}

/** Le nom d'origine ne sert que de repli quand aucun titre n'a été saisi. */
export function mediaLabel(media: Media): string {
  return media.title?.trim() || media.originalFilename;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${ bytes } o`;
  }

  if (bytes < 1024 * 1024) {
    return `${ Math.round(bytes / 1024) } ko`;
  }

  return `${ (bytes / (1024 * 1024)).toFixed(1).replace('.', ',') } Mo`;
}

/**
 * Vérifie un fichier avant envoi. Le back refait ces contrôles — et sur le
 * contenu réel, pas sur ce que déclare le navigateur : ceci ne remplace rien,
 * cela évite seulement un aller-retour perdu.
 */
export function rejectionReason(file: File): string | null {
  if (file.size === 0) {
    return 'fichier vide';
  }

  if (file.size > MAX_MEDIA_SIZE_BYTES) {
    return `trop volumineux (${ formatFileSize(file.size) }, maximum ${ formatFileSize(MAX_MEDIA_SIZE_BYTES) })`;
  }

  if (!ACCEPTED_MEDIA_CONTENT_TYPES.includes(file.type as MediaContentType)) {
    return 'format non accepté (JPEG, PNG, WebP ou PDF)';
  }

  return null;
}
