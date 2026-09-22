package fr.maximechazard.agorapilot.back.campaign.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * La campagne existe, mais son état interdit la clôture : elle n'a pas encore
 * commencé, ou elle est déjà terminée.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CampaignNotClosableException extends RuntimeException {

    public CampaignNotClosableException(String message) {
        super(message);
    }
}
