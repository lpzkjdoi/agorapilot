package fr.maximechazard.agorapilot.back.media.storage;

import fr.maximechazard.agorapilot.back.media.MediaFileType;
import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Dépôt des binaires de la médiathèque.
 * <p>
 * Abstraction volontairement étroite : elle ne connaît ni la base ni les
 * transactions. La seule implémentation aujourd'hui écrit sur un volume disque
 * ({@link FileSystemMediaStorage}) ; une variante S3 se substituerait ici sans
 * toucher au service métier.
 */
public interface MediaStorageService {

    /**
     * Écrit le flux et renvoie de quoi le retrouver. Le flux n'est pas fermé par
     * cette méthode : c'est à l'appelant de le faire.
     */
    StoredFile store(InputStream content, MediaFileType fileType);

    Resource load(String storageKey);

    /**
     * Supprime le fichier. Ne lève pas si le fichier a déjà disparu : la
     * suppression doit rester rejouable sans effet de bord.
     */
    void delete(String storageKey);
}
