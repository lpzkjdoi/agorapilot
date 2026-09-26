package fr.maximechazard.agorapilot.back.publication.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * La liste envoyée contient deux fois le même média. Refusé plutôt que
 * dédoublonné en silence : c'est presque toujours une erreur d'appel, et la
 * corriger côté serveur ferait diverger l'ordre de ce que l'appelant croit avoir
 * envoyé.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DuplicateMediaException extends RuntimeException {

    public DuplicateMediaException(String message) {
        super(message);
    }
}
