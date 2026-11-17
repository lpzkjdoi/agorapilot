/**
 * Propose au navigateur d'enregistrer un fichier reçu de l'API, sous le nom
 * donné : un lien temporaire vers le contenu, cliqué puis retiré.
 */
export function saveFile(content: Blob, filename: string): void {
  const url = URL.createObjectURL(content);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.style.display = 'none';

  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/**
 * Nom de fichier annoncé par l'en-tête `Content-Disposition`
 * (`attachment; filename="export.xlsx"`), ou `null` s'il n'en donne pas.
 */
export function filenameFromDisposition(disposition: string | null): string | null {
  const match = disposition?.match(/filename="([^"]+)"/) ?? disposition?.match(/filename=([^;]+)/);

  return match ? match[1].trim() : null;
}
