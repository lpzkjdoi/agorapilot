package fr.maximechazard.agorapilot.back.media.storage;

/**
 * Résultat d'un dépôt sur le stockage.
 *
 * @param storageKey chemin relatif sous la racine, à conserver en base
 * @param sizeBytes  taille réellement écrite
 * @param checksum   SHA-256 du contenu, en hexadécimal minuscule
 */
public record StoredFile(String storageKey, long sizeBytes, String checksum) {
}
