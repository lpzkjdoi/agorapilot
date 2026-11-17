package fr.maximechazard.agorapilot.back.publication.export;

/**
 * Fichier XLSX prêt à télécharger.
 *
 * @param filename nom proposé au navigateur
 * @param content  contenu du classeur
 */
public record EventProxyExport(String filename, byte[] content) {
}
