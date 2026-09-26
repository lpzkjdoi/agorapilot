package fr.maximechazard.agorapilot.back.media.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Le contenu déposé n'est pas d'un format accepté — y compris quand il est vide
 * ou trop court pour être identifié.
 * <p>
 * Nommée {@code MediaFileType} et non {@code MediaType} pour ne pas se confondre
 * avec {@code HttpMediaTypeNotSupportedException} de Spring, qui porte sur le
 * {@code Content-Type} de la requête et non sur le fichier transporté.
 */
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class UnsupportedMediaFileTypeException extends RuntimeException {

    public UnsupportedMediaFileTypeException(String message) {
        super(message);
    }
}
