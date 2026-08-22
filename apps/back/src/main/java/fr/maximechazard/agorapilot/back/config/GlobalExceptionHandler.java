package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.DeliveryFailedException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
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
}
