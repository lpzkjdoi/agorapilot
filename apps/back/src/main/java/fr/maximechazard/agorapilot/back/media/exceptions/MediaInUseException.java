package fr.maximechazard.agorapilot.back.media.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Le média est encore rattaché à au moins une publication.
 * <p>
 * La suppression est refusée plutôt que propagée : effacer le visuel viderait
 * silencieusement des publications existantes, y compris déjà diffusées.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class MediaInUseException extends RuntimeException {

    public MediaInUseException(String message) {
        super(message);
    }
}
