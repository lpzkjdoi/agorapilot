package fr.maximechazard.agorapilot.back.media.storage;

/**
 * Échec technique du stockage : disque plein, droits insuffisants, fichier
 * disparu sous la base. Distincte des erreurs métier, qui se traduisent en 4xx.
 */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String message) {
        super(message);
    }

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
