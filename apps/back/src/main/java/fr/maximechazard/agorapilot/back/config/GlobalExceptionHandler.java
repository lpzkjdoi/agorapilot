package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
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
}
