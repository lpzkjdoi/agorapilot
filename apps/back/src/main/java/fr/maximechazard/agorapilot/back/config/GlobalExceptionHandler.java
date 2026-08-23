package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.media.exceptions.UnsupportedMediaFileTypeException;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageException;
import fr.maximechazard.agorapilot.back.publication.exceptions.DeliveryFailedException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ApiError> handleCampaignNotFoundException(CampaignNotFoundException exception) {
        ApiError apiError = new ApiError();
        apiError.setMessage(exception.getMessage());
        apiError.setCode(HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(PublicationNotFoundException.class)
    public ResponseEntity<ApiError> handlePublicationNotFoundException(PublicationNotFoundException exception) {
        ApiError apiError = new ApiError();
        apiError.setMessage(exception.getMessage());
        apiError.setCode(HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    /**
     * La livraison a été tentée et tracée en base ({@code FAILED}) : c'est le
     * canal distant qui a refusé, d'où un 502 plutôt qu'un 4xx.
     */
    @ExceptionHandler(DeliveryFailedException.class)
    public ResponseEntity<ApiError> handleDeliveryFailedException(DeliveryFailedException exception) {
        ApiError apiError = new ApiError();
        apiError.setMessage(exception.getMessage());
        apiError.setCode(HttpStatus.BAD_GATEWAY.value());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(apiError);
    }

    @ExceptionHandler(UnsupportedDeliveryChannelException.class)
    public ResponseEntity<ApiError> handleUnsupportedDeliveryChannelException(UnsupportedDeliveryChannelException exception) {
        ApiError apiError = new ApiError();
        apiError.setMessage(exception.getMessage());
        apiError.setCode(HttpStatus.NOT_IMPLEMENTED.value());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(apiError);
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ApiError> handleMediaNotFoundException(MediaNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnsupportedMediaFileTypeException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaFileTypeException(UnsupportedMediaFileTypeException exception) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
    }

    /**
     * Sans ce handler, un fichier dépassant {@code spring.servlet.multipart.max-file-size}
     * ressortait en 500 nu, indiscernable d'un bug côté serveur.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Le fichier dépasse la taille maximale autorisée.");
    }

    /**
     * Le disque n'a pas répondu comme attendu : c'est une panne, pas une erreur de
     * l'appelant. Le détail technique reste dans les logs.
     */
    @ExceptionHandler(MediaStorageException.class)
    public ResponseEntity<ApiError> handleMediaStorageException(MediaStorageException exception) {
        log.error("Stockage des médias en échec", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Le stockage des médias est indisponible.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        ApiError apiError = new ApiError();
        apiError.setMessage(message);
        apiError.setCode(status.value());
        return ResponseEntity.status(status).body(apiError);
    }
}
